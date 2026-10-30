package com.myeongro.api.domain.reading.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.myeongro.api.domain.reading.dto.CreatedReadingResponse;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.exception.ReadingGenerationInProgressException;
import com.myeongro.api.domain.reading.exception.ReadingRecordNotFoundException;
import com.myeongro.api.domain.reading.repository.ReadingRecordsRepository;

@Service
public class ReadingRecordsService {

	private final ReadingRecordsRepository repository;

	public ReadingRecordsService(ReadingRecordsRepository repository) {
		this.repository = repository;
	}

	public List<CreatedReadingResponse> listByUser(UUID userId) {
		return repository.listByUser(userId).stream()
			.map(this::toPublicResponse)
			.toList();
	}

	public CreatedReadingResponse getByUserAndId(UUID userId, UUID readingId) {
		return toPublicResponse(findStoredReading(userId, readingId));
	}

	public void deleteByUserAndId(UUID userId, UUID readingId) {
		if (repository.deleteByUserAndId(userId, readingId)) {
			return;
		}
		// Deleting a generating row would free the one-in-flight slot while OpenAI keeps running.
		if ("generating".equals(findStoredReading(userId, readingId).status())) {
			throw new ReadingGenerationInProgressException();
		}
		// Generation finished between the two statements, so the row is deletable now.
		if (!repository.deleteByUserAndId(userId, readingId)) {
			throw new ReadingRecordNotFoundException();
		}
	}

	private CreatedReadingResponse findStoredReading(UUID userId, UUID readingId) {
		return repository.findByUserAndId(userId, readingId)
			.orElseThrow(ReadingRecordNotFoundException::new);
	}

	private CreatedReadingResponse toPublicResponse(CreatedReadingResponse reading) {
		Map<String, Object> redactedInput = new LinkedHashMap<>(reading.input());
		redactedInput.remove("question");
		redactedInput.remove("choiceOptions");
		if (reading.kind() == ReadingKind.TAROT
			&& !"completed".equals(reading.status())) {
			redactedInput.remove("cards");
		}
		return new CreatedReadingResponse(
			reading.id(), reading.kind(), reading.spreadType(), reading.schemaVersion(),
			reading.status(), reading.title(), Map.copyOf(redactedInput), reading.result(),
			reading.errorCode(), reading.createdAt(), reading.updatedAt()
		);
	}
}
