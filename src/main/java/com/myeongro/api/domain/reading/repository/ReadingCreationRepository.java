package com.myeongro.api.domain.reading.repository;

import java.util.Optional;
import java.util.UUID;

import com.myeongro.api.domain.reading.dto.CreatedReadingResponse;
import com.myeongro.api.domain.reading.dto.ReadingResult;

public interface ReadingCreationRepository {

	Optional<CreatedReadingResponse> findExisting(
		UUID userId,
		UUID requestId,
		String inputHash
	);

	PendingReadingCreation createPending(PendingReadingCommand command);

	CreatedReadingResponse completePending(
		PendingReadingCreation pending,
		ReadingResult result
	);

	void failPending(PendingReadingCreation pending, String errorCode);
}
