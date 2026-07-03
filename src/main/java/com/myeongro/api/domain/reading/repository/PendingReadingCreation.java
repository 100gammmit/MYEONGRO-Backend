package com.myeongro.api.domain.reading.repository;

import java.util.UUID;

import com.myeongro.api.domain.reading.dto.CreatedReadingResponse;

public record PendingReadingCreation(
	UUID readingId,
	Long generationId,
	CreatedReadingResponse reading
) {
}
