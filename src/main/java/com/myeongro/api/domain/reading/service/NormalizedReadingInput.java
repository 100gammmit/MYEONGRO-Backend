package com.myeongro.api.domain.reading.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.myeongro.api.domain.reading.entity.ReadingKind;
public record NormalizedReadingInput(
	ReadingKind kind,
	String spreadType,
	int schemaVersion,
	String question,
	Map<String, Object> payload,
	Map<String, Object> hashMaterial
) {

	public NormalizedReadingInput {
		payload = Collections.unmodifiableMap(new LinkedHashMap<>(payload));
		hashMaterial = Collections.unmodifiableMap(new LinkedHashMap<>(hashMaterial));
	}
}
