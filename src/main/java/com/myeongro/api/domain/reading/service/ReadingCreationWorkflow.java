package com.myeongro.api.domain.reading.service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import com.myeongro.api.domain.consent.entity.ConsentScope;
import com.myeongro.api.domain.consent.service.ConsentService;
import com.myeongro.api.domain.reading.dto.CreatedReadingResponse;
import com.myeongro.api.domain.reading.dto.GeneratedReading;
import com.myeongro.api.domain.reading.exception.OpenAiReadingGenerationException;
import com.myeongro.api.domain.reading.exception.ReadingGenerationExpiredException;
import com.myeongro.api.domain.reading.exception.ReadingGenerationInProgressException;
import com.myeongro.api.domain.reading.exception.RequiredConsentMissingException;
import com.myeongro.api.domain.reading.repository.PendingReadingCommand;
import com.myeongro.api.domain.reading.repository.PendingReadingCreation;
import com.myeongro.api.domain.reading.repository.ReadingCreationRepository;
import com.myeongro.api.domain.readingcredit.config.ReadingCreditProperties;

@Service
public class ReadingCreationWorkflow {

	private static final long PREPARATION_BUDGET_NANOS = Duration.ofSeconds(25).toNanos();

	private final ConsentService consentService;
	private final ReadingCreationRepository repository;
	private final ReadingGenerator generator;
	private final ReadingInputFingerprinter inputFingerprinter;
	private final ReadingGenerationMetadataResolver generationMetadataResolver;
	private final ReadingCreditProperties creditProperties;
	private final DirectIdentifierInputGuard directIdentifierInputGuard;

	public ReadingCreationWorkflow(
		ConsentService consentService,
		ReadingCreationRepository repository,
		ReadingGenerator generator,
		ReadingInputFingerprinter inputFingerprinter,
		ReadingGenerationMetadataResolver generationMetadataResolver,
		ReadingCreditProperties creditProperties,
		DirectIdentifierInputGuard directIdentifierInputGuard
	) {
		this.consentService = consentService;
		this.repository = repository;
		this.generator = generator;
		this.inputFingerprinter = inputFingerprinter;
		this.generationMetadataResolver = generationMetadataResolver;
		this.creditProperties = creditProperties;
		this.directIdentifierInputGuard = directIdentifierInputGuard;
	}

	public <T extends ReadingRequestInput> CreatedReadingResponse create(
		UUID userId,
		UUID requestId,
		ConsentScope consentScope,
		Supplier<T> inputSupplier,
		Consumer<T> validateNewRequest,
		Function<T, PreparedReadingInput> pendingInputFinalizer
	) {
		requireCreationAllowed(userId, requestId, consentScope);
		T input = inputSupplier.get();
		validateInput(input);
		String inputHash = inputFingerprinter.fingerprint(input);
		var existing = replay(userId, requestId, input, inputHash);
		if (existing.isPresent()) {
			return existing.get();
		}

		validateNewRequest.accept(input);
		int creditCost = creditProperties.cost(input.kind(), input.spreadType());
		UUID preparationToken = UUID.randomUUID();
		long preparationStartedAt = System.nanoTime();
		if (!repository.acquirePreparation(userId, requestId, preparationToken, creditCost)) {
			return replay(userId, requestId, input, inputHash)
				.orElseThrow(ReadingGenerationExpiredException::new);
		}

		PreparedReadingInput prepared;
		PendingReadingCreation pending;
		try {
			checkPreparationBudget(preparationStartedAt);
			prepared = pendingInputFinalizer.apply(input);
			ReadingGenerationMetadata metadata = generationMetadataResolver.resolve(
				prepared.kind(), prepared.spreadType(), prepared.aiPayload()
			);
			checkPreparationBudget(preparationStartedAt);
			pending = repository.createPending(PendingReadingCommand.builder()
				.userId(userId)
				.requestId(requestId)
				.inputHash(inputHash)
				.kind(prepared.kind())
				.spreadType(prepared.spreadType())
				.schemaVersion(prepared.schemaVersion())
				.input(prepared.storedPayload())
				.provider(metadata.provider())
				.model(metadata.model())
				.promptVersion(metadata.promptVersion())
				.creditCost(creditCost)
				.preparationToken(preparationToken)
				.build());
		} catch (RuntimeException | Error failure) {
			try {
				repository.releasePreparation(userId, preparationToken);
			} catch (RuntimeException cleanupFailure) {
				failure.addSuppressed(cleanupFailure);
			}
			throw failure;
		}
		if (pending.reading().result() != null) {
			return pending.reading();
		}
		return generatePending(prepared, pending);
	}

	private static void checkPreparationBudget(long startedAt) {
		if (System.nanoTime() - startedAt >= PREPARATION_BUDGET_NANOS) {
			throw new ReadingGenerationExpiredException();
		}
	}

	private Optional<CreatedReadingResponse> replay(
		UUID userId, UUID requestId, ReadingRequestInput input, String inputHash
	) {
		var existing = repository.findExisting(
			userId,
			requestId,
			input.kind(),
			input.spreadType(),
			input.schemaVersion(),
			inputHash,
			input.idempotencyPayload()
		);
		if (existing.isPresent()) {
			CreatedReadingResponse reading = existing.get();
			if ("completed".equals(reading.status())) {
				return existing;
			}
			if ("failed".equals(reading.status())) {
				throw new OpenAiReadingGenerationException().withReadingId(reading.id());
			}
			if ("generating".equals(reading.status())) {
				throw new ReadingGenerationInProgressException();
			}
		}
		return Optional.empty();
	}

	public CreatedReadingResponse generatePending(
		PreparedReadingInput input,
		PendingReadingCreation pending
	) {
		GeneratedReading result;
		try {
			result = generator.generate(
				input.kind(), input.spreadType(), input.question(), input.aiPayload()
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

	public void validateInput(ReadingRequestInput input) {
		directIdentifierInputGuard.validate(input);
	}

	public void requireConsent(UUID userId, ConsentScope consentScope) {
		if (!consentService.hasAccepted(userId, consentScope)) {
			throw new RequiredConsentMissingException("필수 동의가 필요합니다.");
		}
	}

	private void requireCreationAllowed(
		UUID userId,
		UUID requestId,
		ConsentScope consentScope
	) {
		if (userId == null) {
			throw new IllegalArgumentException("User id is required");
		}
		if (requestId == null) {
			throw new IllegalArgumentException("Request id is required");
		}
		requireConsent(userId, consentScope);
	}
}
