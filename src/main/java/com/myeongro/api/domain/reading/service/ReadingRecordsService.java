package com.myeongro.api.domain.reading.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.myeongro.api.domain.reading.dto.CreatedReadingResponse;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.exception.ReadingRecordNotFoundException;
import com.myeongro.api.domain.reading.exception.ReadingRetryNotAllowedException;
import com.myeongro.api.domain.reading.repository.PendingReadingCreation;
import com.myeongro.api.domain.reading.repository.ReadingRecordsRepository;
import com.myeongro.api.domain.readingcredit.config.ReadingCreditProperties;

@Service
public class ReadingRecordsService {

	private final ReadingRecordsRepository repository;
	private final ReadingCreationWorkflow creationWorkflow;
	private final ReadingGenerationMetadataResolver generationMetadataResolver;
	private final ReadingInputRestorer inputRestorer;
	private final ReadingCreditProperties creditProperties;

	public ReadingRecordsService(
		ReadingRecordsRepository repository,
		ReadingCreationWorkflow creationWorkflow,
		ReadingGenerationMetadataResolver generationMetadataResolver,
		ReadingInputRestorer inputRestorer,
		ReadingCreditProperties creditProperties
	) {
		this.repository = repository;
		this.creationWorkflow = creationWorkflow;
		this.generationMetadataResolver = generationMetadataResolver;
		this.inputRestorer = inputRestorer;
		this.creditProperties = creditProperties;
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
		if (!repository.softDeleteByUserAndId(userId, readingId)) {
			throw new ReadingRecordNotFoundException();
		}
	}

	public CreatedReadingResponse retry(UUID userId, UUID readingId) {
		CreatedReadingResponse currentReading = findStoredReading(userId, readingId);
		if ("completed".equals(currentReading.status())) {
			return toPublicResponse(currentReading);
		}
		NormalizedReadingInput input;
		try {
			input = inputRestorer.restore(currentReading);
		} catch (IllegalArgumentException exception) {
			throw new ReadingRetryNotAllowedException();
		}
		ReadingGenerationMetadata generationMetadata =
			generationMetadataResolver.resolve(input.kind(), input.spreadType());
		PendingReadingCreation pending = repository.startFailedRetry(
			userId,
			readingId,
			generationMetadata,
			creditProperties.cost(input.kind(), input.spreadType()),
			creditProperties.dailyFreeGrant()
		);
		return creationWorkflow.generatePending(input, pending);
	}

	private CreatedReadingResponse findStoredReading(UUID userId, UUID readingId) {
		return repository.findByUserAndId(userId, readingId)
			.orElseThrow(ReadingRecordNotFoundException::new);
	}

	private CreatedReadingResponse toPublicResponse(CreatedReadingResponse reading) {
		if (reading.kind() != ReadingKind.TAROT
			|| "completed".equals(reading.status())
			|| !reading.input().containsKey("cards")) {
			return reading;
		}
		Map<String, Object> redactedInput = new LinkedHashMap<>(reading.input());
		redactedInput.remove("cards");
		return new CreatedReadingResponse(
			reading.id(), reading.kind(), reading.spreadType(), reading.schemaVersion(),
			reading.status(), reading.title(), Map.copyOf(redactedInput), reading.result(),
			reading.errorCode(), reading.createdAt(), reading.updatedAt()
		);
	}
}
