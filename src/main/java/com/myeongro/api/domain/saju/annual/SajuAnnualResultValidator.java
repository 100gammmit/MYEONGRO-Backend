package com.myeongro.api.domain.saju.annual;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.myeongro.api.domain.reading.service.ReadingMode;

public class SajuAnnualResultValidator {
	public void validate(SajuAnnualResult result, SajuAnnualInput input) {
		if (result == null || !"annual_monthly".equals(result.reportType())
			|| result.targetYear() != input.calendar().targetYear() || result.readingMode() == null
			|| result.questionRedirected() == null
			|| (result.questionRedirected() && result.readingMode() == ReadingMode.STANDARD)
			|| (input.question().isBlank() && (result.questionRedirected() || result.readingMode() != ReadingMode.STANDARD))) {
			throw new IllegalArgumentException("Invalid annual report identity or safety mode");
		}
		text(result.title()); text(result.summary()); text(result.disclaimer());
		Set<String> available = input.trustedCalculation().keySet();
		section(result.annualOverview(), available);
		if (!result.annualOverview().evidenceKeys().contains("annualFlow")) {
			throw new IllegalArgumentException("Annual overview requires annual evidence");
		}
		if (result.months() == null || result.months().size() != 12 || result.periods() == null
			|| result.periods().size() != input.calendar().periods().size()) {
			throw new IllegalArgumentException("Incomplete annual report");
		}
		for (int index = 0; index < 12; index++) {
			var month = result.months().get(index);
			if (month == null || month.month() != index + 1) throw new IllegalArgumentException("Invalid month order");
			text(month.heading()); text(month.body());
			var allowed = new HashSet<>(available);
			allowed.removeIf(key -> key.startsWith("monthlyFlow."));
			var expected = input.calendar().months().get(index).periodIds().stream().map(id -> "monthlyFlow." + id).toList();
			allowed.addAll(expected);
			evidence(month.evidenceKeys(), allowed);
			if (month.evidenceKeys().stream().noneMatch(expected::contains)) {
				throw new IllegalArgumentException("Monthly summary needs its own period evidence");
			}
		}
		for (int index = 0; index < result.periods().size(); index++) {
			var period = result.periods().get(index);
			String expected = input.calendar().periods().get(index).id();
			if (period == null || !expected.equals(period.periodId())) throw new IllegalArgumentException("Invalid annual period order");
			text(period.heading()); text(period.body());
			var allowed = new HashSet<>(available);
			allowed.removeIf(key -> key.startsWith("monthlyFlow.") && !key.equals("monthlyFlow." + expected));
			evidence(period.evidenceKeys(), allowed);
			if (!period.evidenceKeys().contains("monthlyFlow." + expected)) {
				throw new IllegalArgumentException("Period requires its own evidence");
			}
		}
		if (input.question().isBlank()) {
			if (result.questionReading() != null) throw new IllegalArgumentException("No fabricated question reading");
		} else { section(result.questionReading(), available); }
	}
	private void section(SajuAnnualResult.Section section, Set<String> available) {
		if (section == null) throw new IllegalArgumentException("Annual section required");
		text(section.heading()); text(section.body()); evidence(section.evidenceKeys(), available);
	}
	private void evidence(List<String> keys, Set<String> available) {
		if (keys == null || keys.isEmpty() || keys.size() != new HashSet<>(keys).size() || !available.containsAll(keys)) {
			throw new IllegalArgumentException("Invalid annual evidence");
		}
	}
	private void text(String text) {
		if (text == null || text.isBlank()) throw new IllegalArgumentException("Annual text required");
	}
}
