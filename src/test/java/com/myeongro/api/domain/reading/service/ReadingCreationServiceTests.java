package com.myeongro.api.domain.reading.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.consent.dto.ConsentStatus;
import com.myeongro.api.domain.consent.entity.ConsentDocumentType;
import com.myeongro.api.domain.consent.service.ConsentService;
import com.myeongro.api.domain.reading.controller.ReadingCreateRequest;
import com.myeongro.api.domain.reading.dto.CreatedReadingResponse;
import com.myeongro.api.domain.reading.dto.GeneratedReading;
import com.myeongro.api.domain.reading.dto.ReadingResult;
import com.myeongro.api.domain.reading.dto.ReadingSection;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.entity.TarotSpreadType;
import com.myeongro.api.domain.reading.exception.OpenAiReadingGenerationException;
import com.myeongro.api.domain.reading.exception.RequiredConsentMissingException;
import com.myeongro.api.domain.reading.repository.PendingReadingCommand;
import com.myeongro.api.domain.reading.repository.PendingReadingCreation;
import com.myeongro.api.domain.reading.repository.ReadingCreationRepository;
import com.myeongro.api.domain.saju.place.SajuBirthPlaceCatalog;
import com.myeongro.api.domain.saju.model.SajuBirthProfileRequest;
import com.myeongro.api.domain.saju.service.SajuReadingInputAssembler;
import com.myeongro.api.domain.saju.calculation.SajuCalculationException;
import org.springframework.core.io.ClassPathResource;

class ReadingCreationServiceTests {

	private static final UUID USER_ID = UUID.fromString("3b413be2-2b81-4802-8c6a-f868a85d8d83");
	private static final UUID REQUEST_ID = UUID.fromString("82ed11d5-2269-438c-9815-42e6f13735f4");
	private static final UUID READING_ID = UUID.fromString("20e84e95-f5ff-4d9d-a6c4-a3c8ea2e2dfc");
	private SajuReadingInputAssembler sajuInputAssembler;

	@Test
	void rejectsUserWithoutRequiredConsentBeforeReservation() {
		ConsentService consentService = org.mockito.Mockito.mock(ConsentService.class);
		when(consentService.getUserStatus(USER_ID)).thenReturn(new ConsentStatus(
			List.of(), ConsentDocumentType.required(), false
		));
		ReadingCreationRepository repository = org.mockito.Mockito.mock(ReadingCreationRepository.class);

		assertThatThrownBy(() -> service(consentService, repository, successfulGenerator())
			.createReading(USER_ID, REQUEST_ID, request()))
			.isInstanceOf(RequiredConsentMissingException.class);

		org.mockito.Mockito.verifyNoInteractions(repository);
	}

	@Test
	void reservesVersionedRelationshipPayloadForAuthenticatedUser() {
		ConsentService consentService = acceptedConsent();
		ReadingCreationRepository repository = org.mockito.Mockito.mock(ReadingCreationRepository.class);
		when(repository.createPending(org.mockito.ArgumentMatchers.any()))
			.thenReturn(pending());
		when(repository.completePending(
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any()
		)).thenReturn(completed());

		service(consentService, repository, successfulGenerator())
			.createReading(USER_ID, REQUEST_ID, request());

		ArgumentCaptor<PendingReadingCommand> command =
			ArgumentCaptor.forClass(PendingReadingCommand.class);
		verify(repository).createPending(command.capture());
		assertThat(command.getValue().userId()).isEqualTo(USER_ID);
		assertThat(command.getValue().spreadType())
			.isEqualTo(TarotSpreadType.RELATIONSHIP_THREE_CARD);
		assertThat(command.getValue().schemaVersion()).isEqualTo(1);
		assertThat(command.getValue().input()).containsOnlyKeys("question", "cards");
	}

	@Test
	void completesDeclinedReadingInsteadOfMarkingGenerationFailed() {
		ConsentService consentService = acceptedConsent();
		ReadingCreationRepository repository = org.mockito.Mockito.mock(ReadingCreationRepository.class);
		when(repository.createPending(org.mockito.ArgumentMatchers.any()))
			.thenReturn(pending());
		when(repository.completePending(
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any()
		)).thenReturn(completed());
		GeneratedReading declined = new DeclinedReadingFactory().create(
			ReadingDeclineReason.FINANCIAL_DECISION
		);
		ReadingGenerator generator = (kind, spread, question, input) -> declined;

		CreatedReadingResponse response = service(consentService, repository, generator)
			.createReading(USER_ID, REQUEST_ID, request());

		ArgumentCaptor<GeneratedReading> generated = ArgumentCaptor.forClass(GeneratedReading.class);
		verify(repository).completePending(
			org.mockito.ArgumentMatchers.any(), generated.capture()
		);
		assertThat(response.status()).isEqualTo("completed");
		assertThat(generated.getValue().payload())
			.containsEntry("resultType", "declined")
			.containsEntry("reasonCode", "FINANCIAL_DECISION");
		verify(repository, never()).failPending(
			org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()
		);
	}

	@Test
	void reservesSajuSchemaVersionTwoWithoutTarotFields() {
		ConsentService consentService = acceptedConsent();
		ReadingCreationRepository repository = org.mockito.Mockito.mock(ReadingCreationRepository.class);
		when(repository.createPending(org.mockito.ArgumentMatchers.any()))
			.thenReturn(pending());
		when(repository.completePending(
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any()
		)).thenReturn(completed());

		service(consentService, repository, successfulGenerator())
			.createReading(USER_ID, REQUEST_ID, new ReadingCreateRequest(
				"saju",
				null,
				"올해 이직운이 궁금해요",
				REQUEST_ID,
				null,
				null,
				new SajuBirthProfileRequest(
					"solar", "1992-08-17", null, "unknown",
					"36", "36110", "unspecified"
				),
				"career"
			));

		ArgumentCaptor<PendingReadingCommand> command =
			ArgumentCaptor.forClass(PendingReadingCommand.class);
		verify(repository).createPending(command.capture());
		assertThat(command.getValue().kind()).isEqualTo(ReadingKind.SAJU);
		assertThat(command.getValue().spreadType()).isNull();
		assertThat(command.getValue().schemaVersion()).isEqualTo(ReadingSchemaVersions.SAJU);
		assertThat(command.getValue().input()).containsOnlyKeys(
			"question", "focusArea", "birthProfile", "targetYear", "calculationSnapshot"
		);
		assertThat(command.getValue().input()).containsEntry("targetYear", 2026);
	}

	@Test
	void returnsFirstSajuPayloadForSameRequestIdWithoutRecalculation() {
		ConsentService consentService = acceptedConsent();
		ReadingCreationRepository repository = org.mockito.Mockito.mock(ReadingCreationRepository.class);
		ReadingGenerator generator = org.mockito.Mockito.mock(ReadingGenerator.class);
		CreatedReadingResponse existing = sajuReading("completed");
		when(repository.findExisting(
			org.mockito.ArgumentMatchers.eq(USER_ID),
			org.mockito.ArgumentMatchers.eq(REQUEST_ID),
			org.mockito.ArgumentMatchers.anyString()
		)).thenReturn(Optional.of(existing));

		CreatedReadingResponse response = service(consentService, repository, generator)
			.createReading(USER_ID, REQUEST_ID, sajuRequest());

		assertThat(response).isSameAs(existing);
		verify(repository, never()).createPending(org.mockito.ArgumentMatchers.any());
		verify(sajuInputAssembler, never()).assemble(
			org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt()
		);
		verifyNoInteractions(generator);
	}

	@Test
	void doesNotReservePendingReadingWhenSajuCalculationFails() {
		ConsentService consentService = acceptedConsent();
		ReadingCreationRepository repository = org.mockito.Mockito.mock(ReadingCreationRepository.class);
		ReadingGenerator generator = org.mockito.Mockito.mock(ReadingGenerator.class);
		ReadingCreationService service = service(consentService, repository, generator);
		org.mockito.Mockito.doThrow(
			new SajuCalculationException(new IllegalStateException("engine"))
		).when(sajuInputAssembler).assemble(
			org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt()
		);

		assertThatThrownBy(() -> service.createReading(USER_ID, REQUEST_ID, sajuRequest()))
			.isInstanceOf(SajuCalculationException.class)
			.hasMessage("사주 계산을 완료하지 못했습니다.");

		verify(repository, never()).createPending(org.mockito.ArgumentMatchers.any());
		verifyNoInteractions(generator);
	}

	@Test
	void marksPendingGenerationFailedAndPropagatesStable502Exception() {
		ConsentService consentService = acceptedConsent();
		ReadingCreationRepository repository = org.mockito.Mockito.mock(ReadingCreationRepository.class);
		when(repository.createPending(org.mockito.ArgumentMatchers.any()))
			.thenReturn(pending());
		ReadingGenerator failing = (kind, spread, question, input) -> {
			throw new OpenAiReadingGenerationException();
		};

		assertThatThrownBy(() -> service(consentService, repository, failing)
			.createReading(USER_ID, REQUEST_ID, request()))
			.isInstanceOfSatisfying(OpenAiReadingGenerationException.class, exception ->
				assertThat(exception.getReadingId()).isEqualTo(READING_ID)
			);

		verify(repository).failPending(
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.eq("OPENAI_READING_GENERATION_FAILED")
		);
	}

	@Test
	void sameRequestReturnsCompletedReadingWithoutCallingGeneratorAgain() {
		ConsentService consentService = acceptedConsent();
		ReadingCreationRepository repository = org.mockito.Mockito.mock(ReadingCreationRepository.class);
		ReadingGenerator generator = org.mockito.Mockito.mock(ReadingGenerator.class);
		when(repository.createPending(org.mockito.ArgumentMatchers.any()))
			.thenReturn(new PendingReadingCreation(READING_ID, 42L, completed()));

		CreatedReadingResponse response = service(consentService, repository, generator)
			.createReading(USER_ID, REQUEST_ID, request());

		assertThat(response.status()).isEqualTo("completed");
		verifyNoInteractions(generator);
	}

	@Test
	void repeatsFailedCreationAsRetryable502WithStoredReadingId() {
		ConsentService consentService = acceptedConsent();
		ReadingCreationRepository repository = org.mockito.Mockito.mock(ReadingCreationRepository.class);
		ReadingGenerator generator = org.mockito.Mockito.mock(ReadingGenerator.class);
		when(repository.findExisting(
			org.mockito.ArgumentMatchers.eq(USER_ID),
			org.mockito.ArgumentMatchers.eq(REQUEST_ID),
			org.mockito.ArgumentMatchers.anyString()
		)).thenReturn(Optional.of(tarotReading("failed")));

		assertThatThrownBy(() -> service(consentService, repository, generator)
			.createReading(USER_ID, REQUEST_ID, request()))
			.isInstanceOfSatisfying(OpenAiReadingGenerationException.class, exception ->
				assertThat(exception.getReadingId()).isEqualTo(READING_ID)
			);

		verify(repository, never()).createPending(org.mockito.ArgumentMatchers.any());
		verifyNoInteractions(generator);
	}

	@Test
	void hashesEquivalentNestedMapsIdenticallyWithoutReorderingArrays() {
		ReadingCreationService service = service(
			acceptedConsent(),
			org.mockito.Mockito.mock(ReadingCreationRepository.class),
			successfulGenerator()
		);
		Map<String, Object> firstCard = new LinkedHashMap<>();
		firstCard.put("cardId", "major-00-fool");
		firstCard.put("position", "today");
		Map<String, Object> secondCard = new LinkedHashMap<>();
		secondCard.put("position", "today");
		secondCard.put("cardId", "major-00-fool");
		Map<String, Object> first = new LinkedHashMap<>();
		first.put("cards", List.of(firstCard));
		first.put("question", "question");
		Map<String, Object> second = new LinkedHashMap<>();
		second.put("question", "question");
		second.put("cards", List.of(secondCard));

		assertThat(service.inputHash(first)).isEqualTo(service.inputHash(second));

		Map<String, Object> differentArrayOrder = new LinkedHashMap<>();
		differentArrayOrder.put("question", "question");
		differentArrayOrder.put("cards", List.of(
			Map.of("cardId", "major-01-magician"),
			Map.of("cardId", "major-00-fool")
		));
		Map<String, Object> originalArrayOrder = new LinkedHashMap<>();
		originalArrayOrder.put("cards", List.of(
			Map.of("cardId", "major-00-fool"),
			Map.of("cardId", "major-01-magician")
		));
		originalArrayOrder.put("question", "question");
		assertThat(service.inputHash(originalArrayOrder))
			.isNotEqualTo(service.inputHash(differentArrayOrder));
	}

	private ReadingCreationService service(
		ConsentService consentService,
		ReadingCreationRepository repository,
		ReadingGenerator generator
	) {
		TarotPromptCatalog catalog = org.mockito.Mockito.mock(TarotPromptCatalog.class);
		SajuPromptCatalog sajuCatalog = org.mockito.Mockito.mock(SajuPromptCatalog.class);
		when(catalog.version(TarotSpreadType.RELATIONSHIP_THREE_CARD))
			.thenReturn("common+cards+relationship");
		sajuInputAssembler = org.mockito.Mockito.mock(SajuReadingInputAssembler.class);
		when(sajuInputAssembler.assemble(
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.anyInt()
		)).thenAnswer(invocation -> {
			NormalizedReadingInput input = invocation.getArgument(0);
			int targetYear = invocation.getArgument(1);
			Map<String, Object> payload = new LinkedHashMap<>(input.payload());
			payload.put("targetYear", targetYear);
			payload.put("calculationSnapshot", Map.of("calculationVersion", "saju-ko-v1"));
			return new NormalizedReadingInput(
				input.kind(), input.spreadType(), input.schemaVersion(), input.question(),
				payload, input.hashMaterial()
			);
		});
		return new ReadingCreationService(
			consentService,
			repository,
			generator,
			new ReadingInputNormalizer(new SajuBirthPlaceCatalog(
				new ObjectMapper(),
				new ClassPathResource("saju/birth-places/kr-admin-v1.json")
			)),
			new ObjectMapper(),
			new ReadingGenerationMetadataResolver("gpt-test", catalog, sajuCatalog),
			new TarotCardSelector("test-only-tarot-selection-secret-32-bytes"),
			sajuInputAssembler,
			Clock.fixed(Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC)
		);
	}

	private ConsentService acceptedConsent() {
		ConsentService service = org.mockito.Mockito.mock(ConsentService.class);
		when(service.getUserStatus(USER_ID)).thenReturn(new ConsentStatus(
			ConsentDocumentType.required(), ConsentDocumentType.required(), true
		));
		return service;
	}

	private ReadingGenerator successfulGenerator() {
		return (kind, spread, question, input) -> new GeneratedReading(
			result().title(),
			new ObjectMapper().convertValue(result(), new com.fasterxml.jackson.core.type.TypeReference<>() {
			})
		);
	}

	private ReadingCreateRequest request() {
		return new ReadingCreateRequest(
			"tarot",
			"relationship_three_card",
			"관계의 흐름이 궁금해요.",
			REQUEST_ID,
			List.of(1, 2, 3),
			null,
			null,
			null
		);
	}

	private ReadingCreateRequest sajuRequest() {
		return new ReadingCreateRequest(
			"saju", null, "올해 이직운이 궁금해요", REQUEST_ID, null, null,
			new SajuBirthProfileRequest(
				"solar", "1992-08-17", null, "unknown",
				"36", "36110", "unspecified"
			),
			"career"
		);
	}

	private CreatedReadingResponse sajuReading(String status) {
		Map<String, Object> payload = Map.of(
			"question", "올해 이직운이 궁금해요",
			"focusArea", "career",
			"birthProfile", Map.of(
				"calendarType", "solar", "birthDate", "1992-08-17",
				"birthTimePrecision", "unknown", "provinceCode", "36",
				"cityCode", "36110", "luckDirectionBasis", "unspecified"
			),
			"targetYear", 2026,
			"calculationSnapshot", Map.of("calculationVersion", "saju-ko-v1")
		);
		return new CreatedReadingResponse(
			READING_ID, ReadingKind.SAJU, null, ReadingSchemaVersions.SAJU,
			status, "사주 리딩", payload, Map.of("title", "사주 리딩"), null,
			Instant.parse("2026-08-05T00:00:00Z"),
			Instant.parse("2026-08-05T00:00:01Z")
		);
	}

	private CreatedReadingResponse tarotReading(String status) {
		return new CreatedReadingResponse(
			READING_ID, ReadingKind.TAROT, TarotSpreadType.RELATIONSHIP_THREE_CARD,
			ReadingSchemaVersions.TAROT, status, "Tarot reading",
			Map.of("question", "question", "cards", List.of()), null,
			"OPENAI_READING_GENERATION_FAILED",
			Instant.parse("2026-08-05T00:00:00Z"),
			Instant.parse("2026-08-05T00:00:01Z")
		);
	}

	private PendingReadingCreation pending() {
		return new PendingReadingCreation(READING_ID, 42L, generating());
	}

	private CreatedReadingResponse generating() {
		return new CreatedReadingResponse(
			READING_ID,
			ReadingKind.TAROT,
			TarotSpreadType.RELATIONSHIP_THREE_CARD,
			1,
			"generating",
			"Generating...",
			Map.of("question", "관계의 흐름이 궁금해요."),
			null,
			null,
			Instant.parse("2026-06-16T00:00:00Z"),
			Instant.parse("2026-06-16T00:00:01Z")
		);
	}

	private CreatedReadingResponse completed() {
		CreatedReadingResponse pending = generating();
		return new CreatedReadingResponse(
			pending.id(), pending.kind(), pending.spreadType(), pending.schemaVersion(),
			"completed", "관계 리딩", pending.input(), result(), null,
			pending.createdAt(), Instant.parse("2026-06-16T00:00:02Z")
		);
	}

	private ReadingResult result() {
		return new ReadingResult(
			"관계 리딩",
			"요약",
			List.of(
				new ReadingSection("my_heart", "내가 가져온 마음", "본문"),
				new ReadingSection("relationship_flow", "관계에서 드러난 흐름", "본문"),
				new ReadingSection("check_point", "내가 확인할 것", "본문")
			),
			List.of("행동 하나", "행동 둘"),
			"오락과 자기 성찰을 위한 참고입니다."
		);
	}
}
