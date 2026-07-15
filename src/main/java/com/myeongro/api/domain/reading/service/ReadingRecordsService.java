package com.myeongro.api.domain.reading.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.myeongro.api.domain.reading.dto.CreatedReadingResponse;
import com.myeongro.api.domain.reading.exception.ReadingRecordNotFoundException;
import com.myeongro.api.domain.reading.exception.ReadingRetryNotAllowedException;
import com.myeongro.api.domain.reading.repository.PendingReadingCreation;
import com.myeongro.api.domain.reading.repository.ReadingRecordsRepository;

@Service
public class ReadingRecordsService {

	private final ReadingRecordsRepository repository;
	private final ReadingCreationService creationService;
	private final ReadingGenerationMetadataResolver generationMetadataResolver;
	private final ReadingInputNormalizer inputNormalizer;

	public ReadingRecordsService(
		ReadingRecordsRepository repository,
		ReadingCreationService creationService,
		ReadingGenerationMetadataResolver generationMetadataResolver,
		ReadingInputNormalizer inputNormalizer
	) {
		this.repository = repository;
		this.creationService = creationService;
		this.generationMetadataResolver = generationMetadataResolver;
		this.inputNormalizer = inputNormalizer;
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
		CreatedReadingResponse currentReading = getByUserAndId(userId, readingId);
		NormalizedReadingInput input;
		try {
			input = inputNormalizer.restore(currentReading);
		} catch (IllegalArgumentException exception) {
			throw new ReadingRetryNotAllowedException();
		}
		ReadingGenerationMetadata generationMetadata =
			generationMetadataResolver.resolve(input.kind(), input.spreadType());
		PendingReadingCreation pending = repository.startFailedRetry(
			userId,
			readingId,
			generationMetadata
		);
		return creationService.generatePending(input, pending);
	}
}
