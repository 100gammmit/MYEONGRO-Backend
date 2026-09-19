package com.myeongro.api.domain.saju.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.myeongro.api.domain.reading.service.ReadingInputSupport;

public record SajuStoredInput(
	int targetYear,
	Map<String, Object> calculationSnapshot
) {

	public SajuStoredInput {
		calculationSnapshot = Collections.unmodifiableMap(
			new LinkedHashMap<>(calculationSnapshot)
		);
	}

	public Map<String, Object> payload() {
		return ReadingInputSupport.orderedMap(
			"targetYear", targetYear,
			"calculationSnapshot", calculationSnapshot
		);
	}
}
