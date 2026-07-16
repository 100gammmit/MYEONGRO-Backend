package com.myeongro.api.domain.reading.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.consent.dto.ConsentStatus;
import com.myeongro.api.domain.consent.entity.ConsentDocumentType;
import com.myeongro.api.domain.consent.service.ConsentService;
import com.myeongro.api.domain.reading.controller.ReadingCreateRequest;
import com.myeongro.api.domain.reading.dto.CreatedReadingResponse;
import com.myeongro.api.domain.reading.dto.ReadingResult;
import com.myeongro.api.domain.reading.dto.ReadingSection;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.entity.TarotSpreadType;
import com.myeongro.api.domain.reading.exception.OpenAiReadingGenerationException;
import com.myeongro.api.domain.reading.exception.RequiredConsentMissingException;
import com.myeongro.api.domain.reading.repository.PendingReadingCommand;
import com.myeongro.api.domain.reading.repository.PendingReadingCreation;
import com.myeongro.api.domain.reading.repository.ReadingCreationRepository;
import com.myeongro.api.domain.tarotdraw.service.CompletedTarotDraw;
import com.myeongro.api.domain.tarotdraw.service.TarotDrawSessionService;

class ReadingCreationServiceTests {

	private static final UUID USER_ID = UUID.fromString("3b413be2-2b81-4802-8c6a-f868a85d8d83");
	private static final UUID REQUEST_ID = UUID.fromString("82ed11d5-2269-438c-9815-42e6f13735f4");
	private static final UUID READING_ID = UUID.fromString("20e84e95-f5ff-4d9d-a6c4-a3c8ea2e2dfc");
	private TarotDrawSessionService drawSessionService;

	@Test
	void rejectsUserWithoutRequiredConsentBeforeReservation() {
		ConsentService consentService = org.mockito.Mockito.mock(ConsentService.class);
		when(consentService.getUserStatus(USER_ID)).thenReturn(new ConsentStatus(
			List.of(), ConsentDocumentType.required(), false
		));
		ReadingCreationRepository repository = org.mockito.Mockito.mock(ReadingCreationRepository.class);

		assertThatThrownBy(() -> service(consentService, repository, successfulGenerator())
			.createUserReading(USER_ID, "127.0.0.1", REQUEST_ID, request()))
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
			.createUserReading(USER_ID, "127.0.0.1", REQUEST_ID, request());

		ArgumentCaptor<PendingReadingCommand> command =
			ArgumentCaptor.forClass(PendingReadingCommand.class);
		verify(repository).createPending(command.capture());
		assertThat(command.getValue().userId()).isEqualTo(USER_ID);
		assertThat(command.getValue().spreadType())
			.isEqualTo(TarotSpreadType.RELATIONSHIP_THREE_CARD);
		assertThat(command.getValue().schemaVersion()).isEqualTo(1);
		assertThat(command.getValue().input()).containsOnlyKeys("question", "cards");
		verify(drawSessionService).resolveAndConsume(
			org.mockito.ArgumentMatchers.eq(USER_ID),
			org.mockito.ArgumentMatchers.eq("draw-session-id"),
			org.mockito.ArgumentMatchers.eq("relationship_three_card"),
			org.mockito.ArgumentMatchers.eq(REQUEST_ID),
			org.mockito.ArgumentMatchers.anyString()
		);
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
			.createUserReading(USER_ID, "127.0.0.1", REQUEST_ID, request()))
			.isInstanceOf(OpenAiReadingGenerationException.class);

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
			.createUserReading(USER_ID, "127.0.0.1", REQUEST_ID, request());

		assertThat(response.status()).isEqualTo("completed");
		verify(drawSessionService).resolveAndConsume(
			org.mockito.ArgumentMatchers.eq(USER_ID),
			org.mockito.ArgumentMatchers.eq("draw-session-id"),
			org.mockito.ArgumentMatchers.eq("relationship_three_card"),
			org.mockito.ArgumentMatchers.eq(REQUEST_ID),
			org.mockito.ArgumentMatchers.anyString()
		);
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
		when(catalog.version(TarotSpreadType.RELATIONSHIP_THREE_CARD))
			.thenReturn("common+cards+relationship");
		drawSessionService = org.mockito.Mockito.mock(
			TarotDrawSessionService.class
		);
		CompletedTarotDraw draw = new CompletedTarotDraw(
			TarotSpreadType.RELATIONSHIP_THREE_CARD,
			List.of("major-00-fool", "major-06-lovers", "major-17-star")
		);
		when(drawSessionService.resolveCompleted(
			USER_ID, "draw-session-id", "relationship_three_card", REQUEST_ID
		)).thenReturn(draw);
		when(drawSessionService.resolveAndConsume(
			org.mockito.ArgumentMatchers.eq(USER_ID),
			org.mockito.ArgumentMatchers.eq("draw-session-id"),
			org.mockito.ArgumentMatchers.eq("relationship_three_card"),
			org.mockito.ArgumentMatchers.eq(REQUEST_ID),
			org.mockito.ArgumentMatchers.anyString()
		)).thenReturn(draw);
		return new ReadingCreationService(
			consentService,
			repository,
			generator,
			new ReadingInputNormalizer(),
			new ObjectMapper(),
			"0123456789abcdef0123456789abcdef",
			new ReadingGenerationMetadataResolver("gpt-test", catalog),
			drawSessionService
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
		return (kind, spread, question, input) -> result();
	}

	private ReadingCreateRequest request() {
		return new ReadingCreateRequest(
			"tarot",
			"relationship_three_card",
			"관계의 흐름이 궁금해요.",
			REQUEST_ID,
			"draw-session-id",
			null,
			null,
			null,
			null
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
