package com.myeongro.api.domain.reading.service;

import java.util.Map;

import com.myeongro.api.domain.reading.entity.ReadingKind;

public interface ReadingRequestInput {

	ReadingKind kind();

	String spreadType();

	int schemaVersion();

	String question();

	Map<String, Object> idempotencyPayload();

	default Map<String, Object> validationPayload() {
		return idempotencyPayload();
	}
}
