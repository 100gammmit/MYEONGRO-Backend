package com.myeongro.api.domain.saju.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.service.ReadingInputSupport;
import com.myeongro.api.domain.reading.service.ReadingRequestInput;
import com.myeongro.api.domain.saju.model.SajuFocusArea;

public record SajuCalculationInput(
	int schemaVersion,
	String question,
	SajuFocusArea focusArea,
	Map<String, Object> birthProfile
) implements ReadingRequestInput {

	public SajuCalculationInput {
		birthProfile = Collections.unmodifiableMap(new LinkedHashMap<>(birthProfile));
	}

	@Override
	public ReadingKind kind() {
		return ReadingKind.SAJU;
	}

	@Override
	public String spreadType() {
		return null;
	}

	@Override
	public Map<String, Object> idempotencyPayload() {
		return ReadingInputSupport.orderedMap(
			"question", question,
			"focusArea", focusArea.value(),
			"birthProfile", birthProfile
		);
	}
}
