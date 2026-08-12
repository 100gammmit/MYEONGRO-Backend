package com.myeongro.api.domain.reading.service;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.myeongro.api.domain.consent.service.ConsentService;
import com.myeongro.api.domain.reading.controller.ReadingCreateRequest;
import com.myeongro.api.domain.reading.dto.CreatedReadingResponse;
import com.myeongro.api.domain.reading.dto.GeneratedReading;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.entity.TarotSpreadType;
import com.myeongro.api.domain.reading.exception.OpenAiReadingGenerationException;
import com.myeongro.api.domain.reading.exception.RequiredConsentMissingException;
import com.myeongro.api.domain.reading.repository.PendingReadingCommand;
import com.myeongro.api.domain.reading.repository.PendingReadingCreation;
import com.myeongro.api.domain.reading.repository.ReadingCreationRepository;
import com.myeongro.api.domain.saju.calculation.SajuCalculationRules;
import com.myeongro.api.domain.saju.service.SajuReadingInputAssembler;

@Service
public class ReadingCreationService {

	private final ConsentService consentService;
	private final ReadingCreationRepository repository;
	private final ReadingGenerator generator;
	private final ReadingInputNormalizer inputNormalizer;
	private final ObjectMapper objectMapper;
	private final ReadingGenerationMetadataResolver generationMetadataResolver;
	private final TarotCardSelector tarotCardSelector;
	private final SajuReadingInputAssembler sajuInputAssembler;
	private final Clock clock;

	@Autowired
	public ReadingCreationService(
		ConsentService consentService,
		ReadingCreationRepository repository,
		ReadingGenerator generator,
		ReadingInputNormalizer inputNormalizer,
		ObjectMapper objectMapper,
		ReadingGenerationMetadataResolver generationMetadataResolver,
		TarotCardSelector tarotCardSelector,
		SajuReadingInputAssembler sajuInputAssembler
	) {
		this(
			consentService, repository, generator, inputNormalizer, objectMapper,
			generationMetadataResolver, tarotCardSelector, sajuInputAssembler,
			Clock.systemUTC()
		);
	}

	ReadingCreationService(
		ConsentService consentService,
		ReadingCreationRepository repository,
		ReadingGenerator generator,
		ReadingInputNormalizer inputNormalizer,
		ObjectMapper objectMapper,
		ReadingGenerationMetadataResolver generationMetadataResolver,
		TarotCardSelector tarotCardSelector,
		SajuReadingInputAssembler sajuInputAssembler,
		Clock clock
	) {
		this.consentService = consentService;
		this.repository = repository;
		this.generator = generator;
		this.inputNormalizer = inputNormalizer;
		this.objectMapper = objectMapper;
		this.generationMetadataResolver = generationMetadataResolver;
		this.tarotCardSelector = tarotCardSelector;
		this.sajuInputAssembler = sajuInputAssembler;
		this.clock = clock;
	}

	public CreatedReadingResponse createReading(
		UUID userId,
		UUID requestId,
		ReadingCreateRequest request
	) {
		if (userId == null) {
			throw new IllegalArgumentException("User id is required");
		}
		if (requestId == null) {
			throw new IllegalArgumentException("Request id is required");
		}
		if (!consentService.getUserStatus(userId).hasAcceptedRequired()) {
			throw new RequiredConsentMissingException("필수 동의가 필요합니다.");
		}

		NormalizedReadingInput input;
		if (ReadingKind.fromValue(request.kind()) == ReadingKind.TAROT) {
			TarotSpreadType spread = TarotSpreadType.fromValue(request.spreadType());
			var cardIds = tarotCardSelector.select(
				userId, requestId, spread, request.selectedSlots()
			);
			input = inputNormalizer.normalizeTarot(request, spread, cardIds);
		} else {
			input = inputNormalizer.normalize(request);
		}
		String inputHash = inputHash(input.hashMaterial());
		if (input.kind() == ReadingKind.SAJU) {
			var existing = repository.findExisting(userId, requestId, inputHash);
			if (existing.isPresent()) {
				return existing.get();
			}
			int targetYear = LocalDate.ofInstant(
				clock.instant(), SajuCalculationRules.BIRTH_ZONE
			).getYear();
			input = sajuInputAssembler.assemble(input, targetYear);
		}
		ReadingGenerationMetadata metadata = generationMetadataResolver.resolve(
			input.kind(),
			input.spreadType()
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
				input.kind(),
				input.spreadType(),
				input.question(),
				input.payload()
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

}
