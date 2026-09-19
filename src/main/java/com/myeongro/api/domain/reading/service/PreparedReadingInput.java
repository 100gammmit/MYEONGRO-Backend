package com.myeongro.api.domain.reading.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.myeongro.api.domain.reading.entity.ReadingKind;

public record PreparedReadingInput(
	ReadingKind kind,
	String spreadType,
	int schemaVersion,
	String question,
	Map<String, Object> aiPayload,
	Map<String, Object> storedPayload
) {

	public PreparedReadingInput {
		aiPayload = immutable(aiPayload);
		storedPayload = immutable(storedPayload);
	}

	public static PreparedReadingInput fromNormalized(NormalizedReadingInput input) {
		return new PreparedReadingInput(
			input.kind(),
			input.spreadType(),
			input.schemaVersion(),
			input.question(),
			input.payload(),
			input.storedPayload()
		);
	}

	private static Map<String, Object> immutable(Map<String, Object> value) {
		return Collections.unmodifiableMap(new LinkedHashMap<>(value));
	}
}
