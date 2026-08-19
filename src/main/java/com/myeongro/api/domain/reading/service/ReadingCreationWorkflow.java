package com.myeongro.api.domain.reading.service;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.myeongro.api.domain.consent.service.ConsentService;
import com.myeongro.api.domain.reading.dto.CreatedReadingResponse;
import com.myeongro.api.domain.reading.dto.GeneratedReading;
import com.myeongro.api.domain.reading.exception.OpenAiReadingGenerationException;
import com.myeongro.api.domain.reading.exception.RequiredConsentMissingException;
import com.myeongro.api.domain.reading.repository.PendingReadingCommand;
import com.myeongro.api.domain.reading.repository.PendingReadingCreation;
import com.myeongro.api.domain.reading.repository.ReadingCreationRepository;

@Service
public class ReadingCreationWorkflow {

	private final ConsentService consentService;
	private final ReadingCreationRepository repository;
	private final ReadingGenerator generator;
	private final ObjectMapper objectMapper;
	private final ReadingGenerationMetadataResolver generationMetadataResolver;

	public ReadingCreationWorkflow(
		ConsentService consentService,
		ReadingCreationRepository repository,
		ReadingGenerator generator,
		ObjectMapper objectMapper,
		ReadingGenerationMetadataResolver generationMetadataResolver
	) {
		this.consentService = consentService;
		this.repository = repository;
		this.generator = generator;
		this.objectMapper = objectMapper;
		this.generationMetadataResolver = generationMetadataResolver;
	}

	public CreatedReadingResponse create(
		UUID userId,
		UUID requestId,
		Supplier<NormalizedReadingInput> inputSupplier,
		UnaryOperator<NormalizedReadingInput> pendingInputFinalizer
	) {
		requireCreationAllowed(userId, requestId);
		NormalizedReadingInput input = inputSupplier.get();
		String inputHash = inputHash(input.hashMaterial());
		var existing = repository.findExisting(userId, requestId, inputHash);
		if (existing.isPresent()) {
			CreatedReadingResponse reading = existing.get();
			if ("completed".equals(reading.status())) {
				return reading;
			}
			if ("failed".equals(reading.status())) {
				throw new OpenAiReadingGenerationException().withReadingId(reading.id());
			}
		}

		input = pendingInputFinalizer.apply(input);
		ReadingGenerationMetadata metadata = generationMetadataResolver.resolve(
			input.kind(), input.spreadType()
		);
		PendingReadingCreation pending = repository.createPending(PendingReadingCommand.builder()
			.userId(userId)
			.requestId(requestId)
			.inputHash(inputHash)
			.kind(input.kind())
			.spreadType(input.spreadType())
			.schemaVersion(input.schemaVersion())
			.input(input.payload())
			.provider(metadata.provider())
			.model(metadata.model())
			.promptVersion(metadata.promptVersion())
			.build());
		if (pending.reading().result() != null) {
			return pending.reading();
		}
		return generatePending(input, pending);
	}

	public CreatedReadingResponse generatePending(
		NormalizedReadingInput input,
		PendingReadingCreation pending
	) {
		GeneratedReading result;
		try {
			result = generator.generate(
				input.kind(), input.spreadType(), input.question(), input.payload()
			);
		} catch (OpenAiReadingGenerationException exception) {
			repository.failPending(pending, exception.getCode());
			throw exception.withReadingId(pending.readingId());
		} catch (RuntimeException exception) {
			repository.failPending(pending, "READING_GENERATION_FAILED");
			throw exception;
		}
		return repository.completePending(pending, result);
	}

	String inputHash(Map<String, Object> input) {
		try {
			byte[] json = objectMapper.writer()
				.with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
				.writeValueAsBytes(input);
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(json);
			return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
		} catch (GeneralSecurityException | JsonProcessingException exception) {
			throw new IllegalStateException("Cannot hash reading input", exception);
		}
	}

	private void requireCreationAllowed(UUID userId, UUID requestId) {
		if (userId == null) {
			throw new IllegalArgumentException("User id is required");
		}
		if (requestId == null) {
			throw new IllegalArgumentException("Request id is required");
		}
		if (!consentService.getUserStatus(userId).hasAcceptedRequired()) {
			throw new RequiredConsentMissingException("필수 동의가 필요합니다.");
		}
	}
}
