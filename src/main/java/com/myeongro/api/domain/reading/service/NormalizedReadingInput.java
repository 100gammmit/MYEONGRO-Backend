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

	/**
	 * Question and choice text stay out of the stored payload but are part of the
	 * request identity; the fingerprint is an HMAC, so the digest does not expose them.
	 */
	@Override
	public Map<String, Object> idempotencyPayload() {
		return payload;
	}

	@Override
	public Map<String, Object> validationPayload() {
		return payload;
	}
}
