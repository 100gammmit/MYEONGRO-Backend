package com.myeongro.api.domain.saju.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.myeongro.api.domain.saju.result.SajuReadingResult;
import com.myeongro.api.domain.saju.result.SajuReadingSection;

@Component
public class SajuReadingResultValidator {

	static final List<String> SECTION_IDS = List.of(
		"core", "strengths", "relationship", "work"
	);
	private static final Set<String> FOCUS_AREAS = Set.of(
		"self", "career", "relationship", "life_money"
	);
	private static final Set<String> INTERPRETATION_EVIDENCE_KEYS = Set.of(
		"pillars", "dayMaster", "elementBalance", "tenGods", "interactions",
		"currentLuckCycle", "annualFlow", "limitations", "uncertainty"
	);
	private static final List<String> CERTAINTY_MARKERS = List.of(
		"반드시", "틀림없이", "무조건", "100%", "확실하게", "확정적으로", "정해진 운명"
	);

	public void validate(
		SajuReadingResult result,
		int targetYear,
		String focusArea,
		Map<String, Object> trustedCalculation
	) {
		if (result == null || trustedCalculation == null) {
			throw new IllegalArgumentException("Saju result and calculation are required");
		}
		requireText(result.title(), "Saju reading title is required");
		if (result.readingMode() == null) {
			throw new IllegalArgumentException("Saju reading mode is required");
		}
		requireText(result.summary(), "Saju reading summary is required");
		requireText(result.disclaimer(), "Saju reading disclaimer is required");

		List<SajuReadingSection> sections = result.natalSections();
		if (sections == null || sections.size() != SECTION_IDS.size()) {
			throw new IllegalArgumentException("Saju natal section count is invalid");
		}
		Set<String> availableEvidence = new HashSet<>(
			availableEvidenceKeys(trustedCalculation)
		);
		for (int index = 0; index < SECTION_IDS.size(); index++) {
			SajuReadingSection section = sections.get(index);
			if (section == null || !SECTION_IDS.get(index).equals(section.id())) {
				throw new IllegalArgumentException("Saju natal section order is invalid");
			}
			requireText(section.heading(), "Saju section heading is required");
			requireText(section.body(), "Saju section body is required");
			validateEvidence(section.evidenceKeys(), availableEvidence);
		}

		SajuReadingResult.AnnualReading annual = result.annualReading();
		if (annual == null || annual.year() != targetYear) {
			throw new IllegalArgumentException("Saju annual reading year is invalid");
		}
		requireText(annual.heading(), "Saju annual heading is required");
		requireText(annual.body(), "Saju annual body is required");
		validateEvidence(annual.evidenceKeys(), availableEvidence);

		SajuReadingResult.QuestionReading question = result.questionReading();
		if (question == null || !FOCUS_AREAS.contains(focusArea)
			|| !focusArea.equals(question.focusArea())) {
			throw new IllegalArgumentException("Saju question focus area is invalid");
		}
		requireText(question.heading(), "Saju question heading is required");
		requireText(question.body(), "Saju question body is required");
		validateEvidence(question.evidenceKeys(), availableEvidence);

		if (result.guidance() == null
			|| result.guidance().size() < 1
			|| result.guidance().size() > 2) {
			throw new IllegalArgumentException("Saju guidance count is invalid");
		}
		result.guidance().forEach(item -> requireText(item, "Saju guidance is required"));

		if (isTimeUncertain(trustedCalculation)) {
			Stream<String> prose = Stream.concat(
				Stream.of(result.summary(), annual.body(), question.body()),
				Stream.concat(
					sections.stream().map(SajuReadingSection::body),
					result.guidance().stream()
				)
			);
			if (prose.anyMatch(this::containsCertaintyMarker)) {
				throw new IllegalArgumentException("Uncertain saju result uses definitive language");
			}
		}
	}

	List<String> availableEvidenceKeys(Map<String, Object> trustedCalculation) {
		return trustedCalculation.keySet().stream()
			.filter(INTERPRETATION_EVIDENCE_KEYS::contains)
			.sorted()
			.toList();
	}

	private void validateEvidence(List<String> evidenceKeys, Set<String> availableEvidence) {
		if (evidenceKeys == null || evidenceKeys.isEmpty()
			|| new HashSet<>(evidenceKeys).size() != evidenceKeys.size()
			|| evidenceKeys.stream().anyMatch(key -> !availableEvidence.contains(key))) {
			throw new IllegalArgumentException("Saju evidence key is invalid");
		}
	}

	private boolean isTimeUncertain(Map<String, Object> trustedCalculation) {
		Object uncertainty = trustedCalculation.get("uncertainty");
		if (!(uncertainty instanceof Map<?, ?> values)) {
			return false;
		}
		Object candidateCount = values.get("candidateCount");
		return !"exact".equals(values.get("precision"))
			|| candidateCount instanceof Number number && number.intValue() > 1;
	}

	private boolean containsCertaintyMarker(String value) {
		return CERTAINTY_MARKERS.stream().anyMatch(value::contains);
	}

	private void requireText(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(message);
		}
	}
}
