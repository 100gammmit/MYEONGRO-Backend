package com.myeongro.api.domain.reading.repository;

import com.myeongro.api.domain.reading.dto.CreatedReadingResponse;
import com.myeongro.api.domain.reading.dto.ReadingResult;

public interface ReadingCreationRepository {

	PendingReadingCreation createPending(PendingReadingCommand command);

	CreatedReadingResponse completePending(
		PendingReadingCreation pending,
		ReadingResult result
	);

	void failPending(PendingReadingCreation pending, String errorCode);
}
