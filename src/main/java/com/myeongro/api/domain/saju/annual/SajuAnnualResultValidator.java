package com.myeongro.api.domain.saju.annual;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.myeongro.api.domain.reading.service.ReadingMode;

public class SajuAnnualResultValidator {
	public void validate(SajuAnnualResult result, SajuAnnualInput input) {
		if (result == null || !"annual_monthly".equals(result.reportType())
			|| !input.detailLevel().equals(result.detailLevel())
			|| result.targetYear() != input.calendar().targetYear() || result.readingMode() == null
			|| result.questionRedirected() == null
			|| (result.questionRedirected() && result.readingMode() == ReadingMode.STANDARD)
			|| (input.question().isBlank() && (result.questionRedirected() || result.readingMode() != ReadingMode.STANDARD))) {
			throw new SajuAnnualContractViolation(SajuAnnualContractViolation.Code.IDENTITY);
		}
		text(result.title()); text(result.summary()); text(result.disclaimer());
		Set<String> available = input.trustedCalculation().keySet();
		section(result.annualOverview(), available, "annualOverview.evidenceKeys");
		if (!result.annualOverview().evidenceKeys().contains("annualFlow")) {
			throw new SajuAnnualContractViolation(SajuAnnualContractViolation.Code.OVERVIEW_EVIDENCE,
				SajuAnnualContractViolation.EvidenceIssue.MISSING_REQUIRED, "annualOverview.evidenceKeys");
		}
		if (result.months() == null || result.months().size() != 12 || result.periods() == null
			|| result.periods().size() != ("brief".equals(input.detailLevel()) ? 0 : input.calendar().periods().size())) {
			throw new SajuAnnualContractViolation(SajuAnnualContractViolation.Code.INCOMPLETE);
		}
		for (int index = 0; index < 12; index++) {
			var month = result.months().get(index);
			if (month == null || month.month() != index + 1) throw new SajuAnnualContractViolation(SajuAnnualContractViolation.Code.MONTH_ORDER);
			text(month.heading()); text(month.body());
			var allowed = new HashSet<>(available);
			allowed.removeIf(key -> key.startsWith("monthlyFlow."));
			var expected = input.calendar().months().get(index).periodIds().stream().map(id -> "monthlyFlow." + id).toList();
			allowed.addAll(expected);
			String path = "months.m%02d.evidenceKeys".formatted(index + 1);
			evidence(month.evidenceKeys(), allowed, path);
			if (!month.evidenceKeys().containsAll(expected)) {
				throw new SajuAnnualContractViolation(SajuAnnualContractViolation.Code.MONTH_EVIDENCE,
					SajuAnnualContractViolation.EvidenceIssue.MISSING_REQUIRED, path);
			}
		}
		for (int index = 0; index < result.periods().size(); index++) {
			var period = result.periods().get(index);
			String expected = input.calendar().periods().get(index).id();
			if (period == null || !expected.equals(period.periodId())) throw new SajuAnnualContractViolation(SajuAnnualContractViolation.Code.PERIOD_ORDER);
			text(period.heading()); text(period.body());
			var allowed = new HashSet<>(available);
			allowed.removeIf(key -> key.startsWith("monthlyFlow.") && !key.equals("monthlyFlow." + expected));
			String path = "periods." + expected + ".evidenceKeys";
			evidence(period.evidenceKeys(), allowed, path);
			if (!period.evidenceKeys().contains("monthlyFlow." + expected)) {
				throw new SajuAnnualContractViolation(SajuAnnualContractViolation.Code.PERIOD_EVIDENCE,
					SajuAnnualContractViolation.EvidenceIssue.MISSING_REQUIRED, path);
			}
		}
		if (input.question().isBlank()) {
			if (result.questionReading() != null) throw new SajuAnnualContractViolation(SajuAnnualContractViolation.Code.FABRICATED_QUESTION);
		} else { section(result.questionReading(), available, "questionReading.evidenceKeys"); }
	}
	private void section(SajuAnnualResult.Section section, Set<String> available, String path) {
		if (section == null) throw new SajuAnnualContractViolation(SajuAnnualContractViolation.Code.SECTION_REQUIRED);
		text(section.heading()); text(section.body()); evidence(section.evidenceKeys(), available, path);
	}
	private void evidence(List<String> keys, Set<String> available, String path) {
		SajuAnnualContractViolation.EvidenceIssue issue = null;
		if (keys == null) issue = SajuAnnualContractViolation.EvidenceIssue.NULL;
		else if (keys.isEmpty()) issue = SajuAnnualContractViolation.EvidenceIssue.EMPTY;
		else if (!available.containsAll(keys)) issue = SajuAnnualContractViolation.EvidenceIssue.UNSUPPORTED;
		else if (keys.size() != new HashSet<>(keys).size()) issue = SajuAnnualContractViolation.EvidenceIssue.DUPLICATE;
		if (issue != null) throw new SajuAnnualContractViolation(SajuAnnualContractViolation.Code.EVIDENCE, issue, path);
	}
	private void text(String text) {
		if (text == null || text.isBlank()) throw new SajuAnnualContractViolation(SajuAnnualContractViolation.Code.TEXT_REQUIRED);
	}
}
