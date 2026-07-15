package com.myeongro.api.domain.reading.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.consent.service.ConsentService;
import com.myeongro.api.domain.reading.controller.ReadingCreateRequest;
import com.myeongro.api.domain.reading.dto.CreatedReadingResponse;
import com.myeongro.api.domain.reading.dto.ReadingResult;
import com.myeongro.api.domain.reading.exception.OpenAiReadingGenerationException;
import com.myeongro.api.domain.reading.exception.RequiredConsentMissingException;
import com.myeongro.api.domain.reading.repository.PendingReadingCommand;
import com.myeongro.api.domain.reading.repository.PendingReadingCreation;
import com.myeongro.api.domain.reading.repository.ReadingCreationRepository;

@Service
public class ReadingCreationService {

	private final ConsentService consentService;
	private final ReadingCreationRepository repository;
	private final ReadingGenerator generator;
	private final ReadingInputNormalizer inputNormalizer;
	private final ObjectMapper objectMapper;
	private final String ipHashSecret;
	private final ReadingGenerationMetadataResolver generationMetadataResolver;

	public ReadingCreationService(
		ConsentService consentService,
		ReadingCreationRepository repository,
		ReadingGenerator generator,
		ReadingInputNormalizer inputNormalizer,
		ObjectMapper objectMapper,
		@Value("${app.reading.ip-hash-secret}") String ipHashSecret,
		ReadingGenerationMetadataResolver generationMetadataResolver
	) {
		this.consentService = consentService;
		this.repository = repository;
		this.generator = generator;
		this.inputNormalizer = inputNormalizer;
		this.objectMapper = objectMapper;
		this.ipHashSecret = ipHashSecret;
		this.generationMetadataResolver = generationMetadataResolver;
	}

	public CreatedReadingResponse createUserReading(
		UUID userId,
		String remoteAddress,
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

		NormalizedReadingInput input = inputNormalizer.normalize(request);
		ReadingGenerationMetadata metadata = generationMetadataResolver.resolve(
			input.kind(),
			input.spreadType()
		);
		PendingReadingCreation pending = repository.createPending(PendingReadingCommand.builder()
			.userId(userId)
			.requestId(requestId)
			.inputHash(inputHash(input.hashMaterial()))
			.ipHash(ipHash(remoteAddress))
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
		ReadingResult result;
		try {
			result = generator.generate(
				input.kind(),
				input.spreadType(),
				input.question(),
				input.payload()
			);
		} catch (OpenAiReadingGenerationException exception) {
			repository.failPending(pending, exception.getCode());
			throw exception;
		} catch (RuntimeException exception) {
			repository.failPending(pending, "READING_GENERATION_FAILED");
			throw exception;
		}
		return repository.completePending(pending, result);
	}

	private String inputHash(Map<String, Object> input) {
		try {
			byte[] json = objectMapper.writeValueAsBytes(input);
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(json);
			return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
		} catch (GeneralSecurityException | JsonProcessingException exception) {
			throw new IllegalStateException("Cannot hash reading input", exception);
		}
	}

	private String ipHash(String remoteAddress) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(
				ipHashSecret.getBytes(StandardCharsets.UTF_8),
				"HmacSHA256"
			));
			return Base64.getUrlEncoder().withoutPadding()
				.encodeToString(mac.doFinal(
					(remoteAddress == null ? "unknown" : remoteAddress)
						.getBytes(StandardCharsets.UTF_8)
				));
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("Cannot hash request IP", exception);
		}
	}
}
