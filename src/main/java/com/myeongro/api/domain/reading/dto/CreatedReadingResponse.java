package com.myeongro.api.domain.reading.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.entity.TarotSpreadType;

public record CreatedReadingResponse(
	UUID id,
	ReadingKind kind,
	TarotSpreadType spreadType,
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
