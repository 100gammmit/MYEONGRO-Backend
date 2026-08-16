package com.myeongro.api.domain.saju.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.service.NormalizedReadingInput;
import com.myeongro.api.domain.saju.calculation.SajuCalculationRules;
import com.myeongro.api.domain.saju.calculation.SajuCalculationService;
import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot;

@Component
public class SajuReadingInputAssembler {

	private static final Set<String> STORED_KEYS = Set.of(
		"question", "focusArea", "birthProfile", "targetYear", "calculationSnapshot"
	);

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
			Collections.unmodifiableMap(payload), input.hashMaterial()
		);
	}

	public NormalizedReadingInput restore(
		NormalizedReadingInput normalizedBase,
		Map<String, Object> storedPayload
	) {
		if (normalizedBase.kind() != ReadingKind.SAJU) {
			return normalizedBase;
		}
		if (!storedPayload.keySet().equals(STORED_KEYS)) {
			throw new IllegalArgumentException("Stored saju payload shape is invalid");
		}
		int targetYear = requiredInteger(storedPayload.get("targetYear"));
		SajuCalculationSnapshot snapshot = decodeSnapshot(
			requiredMap(storedPayload.get("calculationSnapshot"))
		);
		if (snapshot.targetYear() != targetYear
			|| !SajuCalculationRules.supports(snapshot.calculationVersion())
			|| !SajuCalculationRules.ENGINE.equals(snapshot.engine())
			|| !SajuCalculationRules.ENGINE_VERSION.equals(snapshot.engineVersion())
			|| snapshot.cityCatalogVersion() == null
			|| snapshot.cityCatalogVersion().isBlank()) {
			throw new IllegalArgumentException("Stored saju calculation metadata is invalid");
		}
		return new NormalizedReadingInput(
			normalizedBase.kind(), normalizedBase.spreadType(), normalizedBase.schemaVersion(),
			normalizedBase.question(), storedPayload, normalizedBase.hashMaterial()
		);
	}

	private SajuCalculationSnapshot decodeSnapshot(Map<String, Object> value) {
		try {
			byte[] json = objectMapper.writeValueAsBytes(value);
			return objectMapper.readerFor(SajuCalculationSnapshot.class)
				.with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
				.readValue(json);
		} catch (Exception exception) {
			throw new IllegalArgumentException("Stored saju calculation snapshot is invalid", exception);
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> requiredMap(Object value) {
		if (value instanceof Map<?, ?> map
			&& map.keySet().stream().allMatch(String.class::isInstance)) {
			return (Map<String, Object>)map;
		}
		throw new IllegalArgumentException("Stored saju value is invalid");
	}

	private int requiredInteger(Object value) {
		if (value instanceof Number number) {
			return number.intValue();
		}
		throw new IllegalArgumentException("Stored target year is invalid");
	}
}
