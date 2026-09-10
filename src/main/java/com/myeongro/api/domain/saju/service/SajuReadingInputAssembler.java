package com.myeongro.api.domain.saju.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.service.NormalizedReadingInput;
import com.myeongro.api.domain.saju.calculation.SajuCalculationRules;
import com.myeongro.api.domain.saju.calculation.SajuCalculationService;
import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot;

@Component
public class SajuReadingInputAssembler {

	private final SajuCalculationService calculationService;
	private final ObjectMapper objectMapper;

	public SajuReadingInputAssembler(
		SajuCalculationService calculationService,
		ObjectMapper objectMapper
	) {
		this.calculationService = calculationService;
		this.objectMapper = objectMapper;
	}

	public NormalizedReadingInput assemble(NormalizedReadingInput input, int targetYear) {
		if (input.kind() != ReadingKind.SAJU) {
			return input;
		}
		Map<String, Object> profile = requiredMap(input.payload().get("birthProfile"));
		SajuCalculationSnapshot snapshot = calculationService.calculate(profile, targetYear);
		Map<String, Object> payload = new LinkedHashMap<>(input.payload());
		payload.put("targetYear", targetYear);
		payload.put("calculationSnapshot", objectMapper.convertValue(
			snapshot, new TypeReference<Map<String, Object>>() {
			}
		));
		return new NormalizedReadingInput(
			input.kind(), input.spreadType(), input.schemaVersion(), input.question(),
			Collections.unmodifiableMap(payload)
		);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> requiredMap(Object value) {
		if (value instanceof Map<?, ?> map
			&& map.keySet().stream().allMatch(String.class::isInstance)) {
			return (Map<String, Object>)map;
		}
		throw new IllegalArgumentException("Stored saju value is invalid");
	}

}
