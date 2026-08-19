package com.myeongro.api.domain.reading.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.myeongro.api.domain.reading.entity.ReadingKind;
public record CreatedReadingResponse(
	UUID id,
	ReadingKind kind,
	String spreadType,
	int schemaVersion,
	String status,
	String title,
	Map<String, Object> input,
	Object result,
	String errorCode,
	Instant createdAt,
	Instant updatedAt
) {
}
