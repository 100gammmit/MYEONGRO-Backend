package com.myeongro.api.domain.reading.repository;

import com.myeongro.api.domain.reading.dto.CreatedReadingResponse;
import com.myeongro.api.domain.reading.dto.ReadingResult;

public interface GuestReadingCacheRepository {

	PendingGuestReadingCache createPending(PendingReadingCommand command);

	CreatedReadingResponse completePending(
		PendingGuestReadingCache pending,
		ReadingResult result
	);

	void failPending(PendingGuestReadingCache pending, String errorCode);
}
