package com.myeongro.api.domain.reading.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.myeongro.api.domain.reading.dto.CreatedReadingResponse;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.repository.PendingReadingCreation;
import com.myeongro.api.domain.reading.repository.ReadingRecordsRepository;

@Service
public class ReadingRecordsService {

	private final ReadingRecordsRepository repository;
	private final ReadingCreationService creationService;
	private final ReadingGenerationMetadata generationMetadata;

	public ReadingRecordsService(
		ReadingRecordsRepository repository,
		ReadingCreationService creationService,
		ReadingGenerationMetadata generationMetadata
	) {
		this.repository = repository;
		this.creationService = creationService;
		this.generationMetadata = generationMetadata;
	}

	public List<CreatedReadingResponse> listByUser(UUID userId) {
		return repository.listByUser(userId);
	}

	public CreatedReadingResponse getByUserAndId(UUID userId, UUID readingId) {
		return repository.findByUserAndId(userId, readingId)
			.orElseThrow(ReadingRecordNotFoundException::new);
	}

	public void deleteByUserAndId(UUID userId, UUID readingId) {
		if (!repository.softDeleteByUserAndId(userId, readingId)) {
			throw new ReadingRecordNotFoundException();
		}
	}

	public CreatedReadingResponse retry(UUID userId, UUID readingId) {
		PendingReadingCreation pending = repository.startFailedRetry(
			userId,
			readingId,
			generationMetadata
		);
		CreatedReadingResponse reading = pending.reading();
		String question = questionFrom(reading.input());
		return creationService.generatePending(
			reading.kind(),
			question,
			reading.input(),
			pending
		);
	}

	private String questionFrom(Map<String, Object> input) {
		Object question = input.get("question");
		if (question instanceof String value && !value.isBlank()) {
			return value;
		}
		throw new IllegalStateException("Reading input does not contain a question");
	}
}
