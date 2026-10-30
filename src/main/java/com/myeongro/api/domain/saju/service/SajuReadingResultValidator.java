package com.myeongro.api.domain.saju.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.myeongro.api.domain.saju.result.SajuReadingResult;
import com.myeongro.api.domain.saju.result.SajuReadingSection;
import com.myeongro.api.domain.reading.service.ReadingMode;

@Component
public class SajuReadingResultValidator {

	static final List<String> SECTION_IDS = List.of(
		"core", "strengths", "relationship", "work"
	);
	private static final Set<String> INTERPRETATION_EVIDENCE_KEYS = Set.of(
		"pillars", "dayMaster", "elementBalance", "tenGods", "interactions",
		"currentLuckCycle", "annualFlow", "limitations", "uncertainty"
	);

	public void validate(
		SajuReadingResult result,
		int targetYear,
		Map<String, Object> trustedCalculation
	) {
		if (result == null || trustedCalculation == null) {
			throw new IllegalArgumentException("Saju result and calculation are required");
		}
		requireText(result.title(), "Saju reading title is required");
		if (result.readingMode() == null) {
			throw new IllegalArgumentException("Saju reading mode is required");
		}
		if (result.questionRedirected() == null) {
			throw new IllegalArgumentException("Saju question redirected flag is required");
		}
		if (Boolean.TRUE.equals(result.questionRedirected()) && result.readingMode() == ReadingMode.STANDARD) {
			throw new IllegalArgumentException("Redirected Saju question requires a fortune reading mode");
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
		if (question == null) {
			throw new IllegalArgumentException("Saju question reading is required");
		}
		requireText(question.heading(), "Saju question heading is required");
		requireText(question.body(), "Saju question body is required");
		validateEvidence(question.evidenceKeys(), availableEvidence);
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

	private void requireText(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(message);
		}
	}
}
