package com.myeongro.api.domain.reading.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.consent.dto.ConsentStatus;
import com.myeongro.api.domain.consent.entity.ConsentDocumentType;
import com.myeongro.api.domain.consent.service.ConsentService;
import com.myeongro.api.domain.reading.controller.ReadingCreateRequest;
import com.myeongro.api.domain.reading.dto.CreatedReadingResponse;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.repository.PendingReadingCommand;
import com.myeongro.api.domain.reading.repository.PendingReadingCreation;
import com.myeongro.api.domain.reading.repository.ReadingCreationRepository;

class ReadingCreationServiceTests {

	private static final UUID GUEST_ID =
		UUID.fromString("9775ff70-5708-45d8-85f8-cb57878bc25d");
	private static final UUID REQUEST_ID =
		UUID.fromString("82ed11d5-2269-438c-9815-42e6f13735f4");
	private static final UUID READING_ID =
		UUID.fromString("20e84e95-f5ff-4d9d-a6c4-a3c8ea2e2dfc");
	private static final UUID GENERATION_ID =
		UUID.fromString("75a85049-44cb-4147-9f73-677a8d9137d7");

	@Test
	void rejectsCreationWhenGuestHasNotAcceptedRequiredConsent() {
		ConsentService consentService = mock(ConsentService.class);
		ReadingCreationRepository repository = mock(ReadingCreationRepository.class);
		when(consentService.getStatus(GUEST_ID)).thenReturn(new ConsentStatus(
			List.of(),
			ConsentDocumentType.required(),
			false
		));
		ReadingCreationService service = service(consentService, repository);

		assertThatThrownBy(() -> service.createGuestReading(
			GUEST_ID,
			"127.0.0.1",
			REQUEST_ID,
			tarotRequest()
		)).isInstanceOf(RequiredConsentMissingException.class);

		verify(repository, never()).createPending(any());
	}

	@Test
	void validatesTarotCardIdsAndReturnsCompletedReadingResult() {
		ConsentService consentService = mock(ConsentService.class);
		ReadingCreationRepository repository = mock(ReadingCreationRepository.class);
		when(consentService.getStatus(GUEST_ID)).thenReturn(new ConsentStatus(
			ConsentDocumentType.required(),
			ConsentDocumentType.required(),
			true
		));
		when(repository.createPending(any())).thenReturn(new PendingReadingCreation(
			READING_ID,
			GENERATION_ID,
			new CreatedReadingResponse(
				READING_ID,
				ReadingKind.TAROT,
				"generating",
				Map.of("question", "오늘의 흐름은?"),
				null,
				null,
				Instant.parse("2026-06-16T00:00:00Z"),
				Instant.parse("2026-06-16T00:00:00Z")
			)
		));
		when(repository.completePending(any(), any())).thenReturn(completedReading());
		ReadingCreationService service = service(consentService, repository);

		CreatedReadingResponse response = service.createGuestReading(
			GUEST_ID,
			"127.0.0.1",
			REQUEST_ID,
			tarotRequest()
		);

		assertThat(response.status()).isEqualTo("completed");
		assertThat(response.result()).isNotNull();
		verify(repository).createPending(any(PendingReadingCommand.class));
		verify(repository).completePending(any(), any());
	}

	@Test
	void rejectsDuplicateTarotCards() {
		ConsentService consentService = mock(ConsentService.class);
		ReadingCreationRepository repository = mock(ReadingCreationRepository.class);
		when(consentService.getStatus(GUEST_ID)).thenReturn(new ConsentStatus(
			ConsentDocumentType.required(),
			ConsentDocumentType.required(),
			true
		));
		ReadingCreationService service = service(consentService, repository);

		assertThatThrownBy(() -> service.createGuestReading(
			GUEST_ID,
			"127.0.0.1",
			REQUEST_ID,
			new ReadingCreateRequest(
				"tarot",
				"오늘의 흐름은?",
				REQUEST_ID,
				List.of("major-00-fool", "major-00-fool", "major-02-high-priestess"),
				null,
				null,
				null
			)
		)).isInstanceOf(IllegalArgumentException.class);

		verify(repository, never()).createPending(any());
	}

	@Test
	void marksPendingReadingFailedWhenGenerationFails() {
		ConsentService consentService = mock(ConsentService.class);
		ReadingCreationRepository repository = mock(ReadingCreationRepository.class);
		when(consentService.getStatus(GUEST_ID)).thenReturn(new ConsentStatus(
			ConsentDocumentType.required(),
			ConsentDocumentType.required(),
			true
		));
		PendingReadingCreation pending = new PendingReadingCreation(
			READING_ID,
			GENERATION_ID,
			new CreatedReadingResponse(
				READING_ID,
				ReadingKind.TAROT,
				"generating",
				Map.of("question", "How is today?"),
				null,
				null,
				Instant.parse("2026-06-16T00:00:00Z"),
				Instant.parse("2026-06-16T00:00:00Z")
			)
		);
		when(repository.createPending(any())).thenReturn(pending);
		ReadingGenerator failingGenerator = (kind, question, input) -> {
			throw new OpenAiReadingGenerationException();
		};
		ReadingCreationService service = new ReadingCreationService(
			consentService,
			repository,
			failingGenerator,
			new ObjectMapper(),
			"test-signing-secret"
		);

		assertThatThrownBy(() -> service.createGuestReading(
			GUEST_ID,
			"127.0.0.1",
			REQUEST_ID,
			tarotRequest()
		)).isInstanceOf(OpenAiReadingGenerationException.class);

		verify(repository).failPending(pending, "OPENAI_READING_GENERATION_FAILED");
		verify(repository, never()).completePending(any(), any());
	}

	private ReadingCreationService service(
		ConsentService consentService,
		ReadingCreationRepository repository
	) {
		return new ReadingCreationService(
			consentService,
			repository,
			new DemoReadingGenerator(),
			new ObjectMapper(),
			"test-signing-secret"
		);
	}

	private CreatedReadingResponse completedReading() {
		return new CreatedReadingResponse(
			READING_ID,
			ReadingKind.TAROT,
			"completed",
			Map.of("question", "오늘의 흐름은?"),
			Map.of(
				"title", "타로 데모 리딩",
				"summary", "세 장의 카드가 현재 흐름을 보여줍니다.",
				"sections", List.of(Map.of(
					"heading", "현재의 흐름",
					"body", "질문과 선택한 카드를 바탕으로 차분히 흐름을 살펴봅니다."
				)),
				"guidance", List.of("작은 행동 하나를 먼저 정하세요."),
				"disclaimer", "이 리딩은 오락과 자기성찰을 위한 참고 자료입니다."
			),
			null,
			Instant.parse("2026-06-16T00:00:00Z"),
			Instant.parse("2026-06-16T00:00:01Z")
		);
	}

	private ReadingCreateRequest tarotRequest() {
		return new ReadingCreateRequest(
			"tarot",
			"오늘의 흐름은?",
			REQUEST_ID,
			List.of(
				"major-00-fool",
				"major-01-magician",
				"major-02-high-priestess"
			),
			null,
			null,
			null
		);
	}
}
