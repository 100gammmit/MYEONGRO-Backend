package com.myeongro.api.domain.reading.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.myeongro.api.domain.reading.dto.CreatedReadingResponse;

public interface ReadingRecordsRepository {

	List<CreatedReadingResponse> listByUser(UUID userId);

	Optional<CreatedReadingResponse> findByUserAndId(UUID userId, UUID readingId);

	boolean softDeleteByUserAndId(UUID userId, UUID readingId);

}
