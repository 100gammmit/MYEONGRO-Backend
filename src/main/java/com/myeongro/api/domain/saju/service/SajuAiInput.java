package com.myeongro.api.domain.saju.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.myeongro.api.domain.reading.service.ReadingInputSupport;

public record SajuAiInput(
	String question,
	String focusArea,
	int targetYear,
	Map<String, Object> calculation
) {

	public SajuAiInput {
		calculation = Collections.unmodifiableMap(new LinkedHashMap<>(calculation));
	}

	public Map<String, Object> payload() {
		return ReadingInputSupport.orderedMap(
			"focusArea", focusArea,
			"targetYear", targetYear,
			"calculation", calculation
		);
	}
}
