package com.myeongro.api.aipreview;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

class SajuPreviewInputValidatorTests {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final AiPreviewCaseLoader loader = new AiPreviewCaseLoader(objectMapper);
	private final SajuPreviewInputValidator validator = new SajuPreviewInputValidator();

	@Test
	void acceptsFrozenSampleInput() {
		assertThatCode(() -> validator.validate(sampleInput())).doesNotThrowAnyException();
	}

	@Test
	void rejectsUnsupportedFocusArea() {
		Map<String, Object> input = sampleInput();
		input.put("focusArea", "unknown");

		assertThatThrownBy(() -> validator.validate(input))
			.hasMessageContaining("관심 분야");
	}

	@Test
	void rejectsMissingSnapshotCoreField() {
		Map<String, Object> input = sampleInput();
		Map<String, Object> snapshot = map(input.get("calculationSnapshot"));
		snapshot.remove("uncertainty");

		assertThatThrownBy(() -> validator.validate(input))
			.hasMessageContaining("uncertainty");
	}

	@Test
	void rejectsAnnualFortuneYearDifferentFromTargetYear() {
		Map<String, Object> input = sampleInput();
		Map<String, Object> snapshot = map(input.get("calculationSnapshot"));
		Map<String, Object> annualFortune = map(snapshot.get("annualFortune"));
		annualFortune.put("year", 2027);

		assertThatThrownBy(() -> validator.validate(input))
			.hasMessageContaining("must match target year");
	}

	private Map<String, Object> sampleInput() {
		return objectMapper.convertValue(
			loader.load("saju", "sample").input(),
			new TypeReference<LinkedHashMap<String, Object>>() {
			}
		);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> map(Object value) {
		return (Map<String, Object>)value;
	}
}
