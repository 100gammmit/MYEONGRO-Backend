package com.myeongro.api.domain.reading.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.repository.PendingReadingCommand;
import com.myeongro.api.domain.reading.repository.PendingReadingCreation;
import com.myeongro.api.domain.reading.repository.ReadingCreationRepository;

@Service
public class ReadingCreationService {

	private static final Set<String> MAJOR_ARCANA_IDS = Set.of(
		"major-00-fool",
		"major-01-magician",
		"major-02-high-priestess",
		"major-03-empress",
		"major-04-emperor",
		"major-05-hierophant",
		"major-06-lovers",
		"major-07-chariot",
		"major-08-strength",
		"major-09-hermit",
		"major-10-wheel-of-fortune",
		"major-11-justice",
		"major-12-hanged-man",
		"major-13-death",
		"major-14-temperance",
		"major-15-devil",
		"major-16-tower",
		"major-17-star",
		"major-18-moon",
		"major-19-sun",
		"major-20-judgement",
		"major-21-world"
	);

	private final ConsentService consentService;
	private final ReadingCreationRepository repository;
	private final ReadingGenerator generator;
	private final ObjectMapper objectMapper;
	private final String signingSecret;
	private final ReadingGenerationMetadata generationMetadata;

	public ReadingCreationService(
		ConsentService consentService,
		ReadingCreationRepository repository,
		ReadingGenerator generator,
		ObjectMapper objectMapper,
		@Value("${app.guest.signing-secret}") String signingSecret,
		ReadingGenerationMetadata generationMetadata
	) {
		this.consentService = consentService;
		this.repository = repository;
		this.generator = generator;
		this.objectMapper = objectMapper;
		this.signingSecret = signingSecret;
		this.generationMetadata = generationMetadata;
	}

	public CreatedReadingResponse createGuestReading(
		UUID guestSessionId,
		String remoteAddress,
		UUID requestId,
		ReadingCreateRequest request
	) {
		if (requestId == null) {
			throw new IllegalArgumentException("Request id is required");
		}
		if (!consentService.getStatus(guestSessionId).hasAcceptedRequired()) {
			throw new RequiredConsentMissingException("필수 동의가 필요합니다.");
		}

		ReadingKind kind = ReadingKind.fromValue(request.kind());
		Map<String, Object> input = toStorageInput(kind, request);
		PendingReadingCreation pending = repository.createPending(new PendingReadingCommand(
			null,
			guestSessionId,
			requestId,
			inputHash(input),
			ipHash(remoteAddress),
			kind,
			input,
			generationMetadata.provider(),
			generationMetadata.model(),
			generationMetadata.promptVersion()
		));
		if (pending.reading().result() != null) {
			return pending.reading();
		}
		ReadingResult result;
		try {
			result = generator.generate(kind, request.question().trim(), input);
		} catch (OpenAiReadingGenerationException exception) {
			repository.failPending(pending, exception.getCode());
			throw exception;
		} catch (RuntimeException exception) {
			repository.failPending(pending, "READING_GENERATION_FAILED");
			throw exception;
		}
		return repository.completePending(pending, result);
	}

	private Map<String, Object> toStorageInput(
		ReadingKind kind,
		ReadingCreateRequest request
	) {
		if (request.question() == null || request.question().isBlank()) {
			throw new IllegalArgumentException("Question is required");
		}
		if (request.question().trim().length() > 300) {
			throw new IllegalArgumentException("Question is too long");
		}
		if (kind == ReadingKind.TAROT) {
			return tarotInput(request);
		}
		return sajuInput(request);
	}

	private Map<String, Object> tarotInput(ReadingCreateRequest request) {
		List<String> cardIds = request.cardIds() == null ? List.of() : request.cardIds();
		if (cardIds.size() != 3) {
			throw new IllegalArgumentException("Exactly three tarot cards are required");
		}
		if (Set.copyOf(cardIds).size() != cardIds.size()) {
			throw new IllegalArgumentException("Duplicate tarot card");
		}
		if (!MAJOR_ARCANA_IDS.containsAll(cardIds)) {
			throw new IllegalArgumentException("Unknown tarot card");
		}
		return Map.of(
			"question", request.question().trim(),
			"cards", List.of(
				card(cardIds.get(0), "past"),
				card(cardIds.get(1), "present"),
				card(cardIds.get(2), "guidance")
			)
		);
	}

	private Map<String, Object> card(String cardId, String position) {
		return Map.of(
			"cardId", cardId,
			"position", position,
			"reversed", false
		);
	}

	private Map<String, Object> sajuInput(ReadingCreateRequest request) {
		if (request.birthDate() == null || request.birthDate().isBlank()) {
			throw new IllegalArgumentException("Birth date is required");
		}
		String gender = request.gender() == null ? "unspecified" : request.gender();
		if (!Set.of("female", "male", "unspecified").contains(gender)) {
			throw new IllegalArgumentException("Invalid gender");
		}
		return Map.of(
			"question", request.question().trim(),
			"profile", Map.of(
				"calendarType", "solar",
				"birthDate", request.birthDate(),
				"birthTime", request.birthTime() == null ? "" : request.birthTime(),
				"gender", gender
			)
		);
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
				signingSecret.getBytes(StandardCharsets.UTF_8),
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
