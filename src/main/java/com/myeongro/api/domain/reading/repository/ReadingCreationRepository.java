package com.myeongro.api.domain.reading.repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.myeongro.api.domain.reading.dto.CreatedReadingResponse;
import com.myeongro.api.domain.reading.dto.GeneratedReading;
import com.myeongro.api.domain.reading.entity.ReadingKind;

public interface ReadingCreationRepository {

	Optional<CreatedReadingResponse> findExisting(
		UUID userId,
		UUID requestId,
		ReadingKind kind,
		String spreadType,
		int schemaVersion,
		Map<String, Object> input
	);

	PendingReadingCreation createPending(PendingReadingCommand command);

	CreatedReadingResponse completePending(
		PendingReadingCreation pending,
		GeneratedReading result
	);

	void failPending(PendingReadingCreation pending, String errorCode);
}
