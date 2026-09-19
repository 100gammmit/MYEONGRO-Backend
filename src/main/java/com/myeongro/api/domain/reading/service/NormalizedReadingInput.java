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
	Map<String, Object> payload
) implements ReadingRequestInput {

	public NormalizedReadingInput {
		payload = Collections.unmodifiableMap(new LinkedHashMap<>(payload));
	}

	public Map<String, Object> storedPayload() {
		Map<String, Object> stored = new LinkedHashMap<>(payload);
		stored.remove("question");
		stored.remove("choiceOptions");
		return Collections.unmodifiableMap(stored);
	}

	@Override
	public Map<String, Object> idempotencyPayload() {
		return storedPayload();
	}

	@Override
	public Map<String, Object> validationPayload() {
		return payload;
	}
}
