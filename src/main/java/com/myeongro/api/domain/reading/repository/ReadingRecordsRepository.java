package com.myeongro.api.domain.reading.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.myeongro.api.domain.reading.dto.CreatedReadingResponse;
import com.myeongro.api.domain.reading.service.ReadingGenerationMetadata;

public interface ReadingRecordsRepository {

	List<CreatedReadingResponse> listByUser(UUID userId);

	Optional<CreatedReadingResponse> findByUserAndId(UUID userId, UUID readingId);

	boolean softDeleteByUserAndId(UUID userId, UUID readingId);

	PendingReadingCreation startFailedRetry(
		UUID userId,
		UUID readingId,
		ReadingGenerationMetadata metadata,
		int creditCost,
		int dailyFreeGrant
	);
}
