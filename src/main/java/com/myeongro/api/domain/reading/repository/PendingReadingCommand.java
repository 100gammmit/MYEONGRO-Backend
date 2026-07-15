package com.myeongro.api.domain.reading.repository;

import java.util.Map;
import java.util.UUID;

import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.entity.TarotSpreadType;

import lombok.Builder;

@Builder
public record PendingReadingCommand(
	UUID userId,
	UUID requestId,
	String inputHash,
	String ipHash,
	ReadingKind kind,
	TarotSpreadType spreadType,
	int schemaVersion,
	Map<String, Object> input,
	String provider,
	String model,
	String promptVersion
) {
}
