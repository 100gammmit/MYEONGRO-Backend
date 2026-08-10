package com.myeongro.api.aipreview;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.myeongro.api.domain.saju.calculation.SajuCalculationRules;
import com.myeongro.api.domain.saju.model.SajuFocusArea;

final class SajuPreviewInputValidator {

	private static final Set<String> INPUT_FIELDS = Set.of(
		"focusArea", "targetYear", "calculationSnapshot"
	);
	private static final Set<String> PILLAR_FIELDS = Set.of(
		"ganZhi", "stem", "branch", "fiveElements", "stemTenGod", "branchTenGods"
	);
	private static final Set<String> ELEMENT_FIELDS = Set.of(
		"wood", "fire", "earth", "metal", "water"
	);

	void validate(Map<String, Object> input) {
		if (!input.keySet().equals(INPUT_FIELDS)) {
			throw new IllegalArgumentException("Saju preview input structure is invalid");
		}
		String focusArea = requiredText(input.get("focusArea"), "Saju preview focus area is required");
		SajuFocusArea.fromValue(focusArea);
		int targetYear = requiredInteger(
			input.get("targetYear"), "Saju preview target year is required"
		);
		Map<?, ?> snapshot = requiredMap(
			input.get("calculationSnapshot"),
			"Saju preview calculation snapshot is required"
		);
		validateSnapshot(snapshot, targetYear);
	}

	private void validateSnapshot(Map<?, ?> snapshot, int targetYear) {
		if (!SajuCalculationRules.CALCULATION_VERSION.equals(snapshot.get("calculationVersion"))) {
			throw new IllegalArgumentException("Saju preview calculation version is invalid");
		}
		Map<?, ?> pillars = requiredMap(
			snapshot.get("pillars"), "Saju preview pillars are required"
		);
		for (String name : List.of("year", "month", "day")) {
			validatePillar(requiredMap(
				pillars.get(name), "Saju preview " + name + " pillar is required"
			));
		}
		requiredText(snapshot.get("dayMaster"), "Saju preview day master is required");
		validateElements(requiredMap(
			snapshot.get("fiveElements"), "Saju preview five elements are required"
		));
		validateRelations(requiredList(
			snapshot.get("relations"), "Saju preview relations are required"
		));
		validateAnnualFortune(
			requiredMap(snapshot.get("annualFortune"), "Saju preview annual fortune is required"),
			targetYear
		);
		validateTextList(requiredList(
			snapshot.get("limitations"), "Saju preview limitations are required"
		), "Saju preview limitations are invalid");
		validateUncertainty(requiredMap(
			snapshot.get("uncertainty"), "Saju preview uncertainty is required"
		));
	}

	private void validatePillar(Map<?, ?> pillar) {
		if (!pillar.keySet().equals(PILLAR_FIELDS)) {
			throw new IllegalArgumentException("Saju preview pillar structure is invalid");
		}
		for (String field : List.of(
			"ganZhi", "stem", "branch", "fiveElements", "stemTenGod"
		)) {
			requiredText(pillar.get(field), "Saju preview pillar text is required");
		}
		validateTextList(requiredList(
			pillar.get("branchTenGods"), "Saju preview branch ten gods are required"
		), "Saju preview branch ten gods are invalid");
	}

	private void validateElements(Map<?, ?> elements) {
		if (!elements.keySet().equals(ELEMENT_FIELDS)
			|| elements.values().stream().anyMatch(value -> !(value instanceof Number))) {
			throw new IllegalArgumentException("Saju preview five elements are invalid");
		}
	}

	private void validateRelations(List<?> relations) {
		for (Object value : relations) {
			Map<?, ?> relation = requiredMap(value, "Saju preview relation is invalid");
			requiredText(relation.get("type"), "Saju preview relation type is required");
			validateTextList(requiredList(
				relation.get("members"), "Saju preview relation members are required"
			), "Saju preview relation members are invalid");
		}
	}

	private void validateAnnualFortune(Map<?, ?> annualFortune, int targetYear) {
		int annualYear = requiredInteger(
			annualFortune.get("year"), "Saju preview annual fortune year is required"
		);
		if (annualYear != targetYear) {
			throw new IllegalArgumentException("Saju preview annual fortune year must match target year");
		}
		requiredText(annualFortune.get("ganZhi"), "Saju preview annual ganZhi is required");
		requiredText(
			annualFortune.get("stemTenGod"), "Saju preview annual stem ten god is required"
		);
	}

	private void validateUncertainty(Map<?, ?> uncertainty) {
		requiredText(
			uncertainty.get("precision"), "Saju preview uncertainty precision is required"
		);
		int candidateCount = requiredInteger(
			uncertainty.get("candidateCount"),
			"Saju preview uncertainty candidate count is required"
		);
		if (candidateCount < 1) {
			throw new IllegalArgumentException("Saju preview candidate count must be positive");
		}
		validateTextList(requiredList(
			uncertainty.get("varyingFields"), "Saju preview varying fields are required"
		), "Saju preview varying fields are invalid");
		validateTextList(requiredList(
			uncertainty.get("candidateZoneOffsets"),
			"Saju preview candidate zone offsets are required"
		), "Saju preview candidate zone offsets are invalid");
	}

	private void validateTextList(List<?> values, String message) {
		if (values.stream().anyMatch(value -> !(value instanceof String text) || text.isBlank())) {
			throw new IllegalArgumentException(message);
		}
	}

	private Map<?, ?> requiredMap(Object value, String message) {
		if (value instanceof Map<?, ?> map) {
			return map;
		}
		throw new IllegalArgumentException(message);
	}

	private List<?> requiredList(Object value, String message) {
		if (value instanceof List<?> list) {
			return list;
		}
		throw new IllegalArgumentException(message);
	}

	private String requiredText(Object value, String message) {
		if (value instanceof String text && !text.isBlank()) {
			return text;
		}
		throw new IllegalArgumentException(message);
	}

	private int requiredInteger(Object value, String message) {
		if (value instanceof Number number
			&& number.doubleValue() == number.intValue()) {
			return number.intValue();
		}
		throw new IllegalArgumentException(message);
	}
}
