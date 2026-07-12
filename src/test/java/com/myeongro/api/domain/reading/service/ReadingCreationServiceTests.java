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
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.consent.dto.ConsentStatus;
import com.myeongro.api.domain.consent.entity.ConsentDocumentType;
import com.myeongro.api.domain.consent.service.ConsentService;
import com.myeongro.api.domain.reading.controller.ReadingCreateRequest;
import com.myeongro.api.domain.reading.dto.CreatedReadingResponse;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.exception.RequiredConsentMissingException;
import com.myeongro.api.domain.reading.repository.GuestReadingCacheRepository;
import com.myeongro.api.domain.reading.repository.PendingReadingCommand;
import com.myeongro.api.domain.reading.repository.PendingGuestReadingCache;
import com.myeongro.api.domain.reading.repository.PendingReadingCreation;
import com.myeongro.api.domain.reading.repository.ReadingCreationRepository;

class ReadingCreationServiceTests {

	private static final UUID GUEST_ID =
		UUID.fromString("9775ff70-5708-45d8-85f8-cb57878bc25d");
	private static final UUID REQUEST_ID =
		UUID.fromString("82ed11d5-2269-438c-9815-42e6f13735f4");
	private static final UUID READING_ID =
		UUID.fromString("20e84e95-f5ff-4d9d-a6c4-a3c8ea2e2dfc");
	private static final Long GENERATION_ID = 42L;

	@Test
	void rejectsCreationWhenGuestHasNotAcceptedRequiredConsent() {
		ConsentService consentService = mock(ConsentService.class);
		ReadingCreationRepository repository = mock(ReadingCreationRepository.class);
		GuestReadingCacheRepository guestCacheRepository = mock(GuestReadingCacheRepository.class);
		when(consentService.getStatus(GUEST_ID)).thenReturn(new ConsentStatus(
			List.of(),
			ConsentDocumentType.required(),
			false
		));
		ReadingCreationService service = service(consentService, repository, guestCacheRepository);

		assertThatThrownBy(() -> service.createGuestReading(
			GUEST_ID,
			"127.0.0.1",
			REQUEST_ID,
			tarotRequest()
		)).isInstanceOf(RequiredConsentMissingException.class);

		verify(repository, never()).createPending(any());
		verify(guestCacheRepository, never()).createPending(any());
	}

	@Test
	void validatesTarotCardIdsAndReturnsCompletedReadingResult() {
		ConsentService consentService = mock(ConsentService.class);
		ReadingCreationRepository repository = mock(ReadingCreationRepository.class);
		GuestReadingCacheRepository guestCacheRepository = mock(GuestReadingCacheRepository.class);
		when(consentService.getStatus(GUEST_ID)).thenReturn(new ConsentStatus(
			ConsentDocumentType.required(),
			ConsentDocumentType.required(),
			true
		));
		when(guestCacheRepository.createPending(any())).thenReturn(new PendingGuestReadingCache(
			READING_ID,
			new CreatedReadingResponse(
				READING_ID,
				ReadingKind.TAROT,
				"generating",
				"Generating...",
				Map.of("question", "오늘의 흐름은?"),
				null,
				null,
				Instant.parse("2026-06-16T00:00:00Z"),
				Instant.parse("2026-06-16T00:00:00Z")
			)
		));
		when(guestCacheRepository.completePending(any(), any())).thenReturn(completedReading());
		ReadingCreationService service = service(consentService, repository, guestCacheRepository);

		CreatedReadingResponse response = service.createGuestReading(
			GUEST_ID,
			"127.0.0.1",
			REQUEST_ID,
			tarotRequest()
		);

		assertThat(response.status()).isEqualTo("completed");
		assertThat(response.result()).isNotNull();
		ArgumentCaptor<PendingReadingCommand> command =
			ArgumentCaptor.forClass(PendingReadingCommand.class);
		verify(guestCacheRepository).createPending(command.capture());
		assertThat(command.getValue().userId()).isNull();
		assertThat(command.getValue().guestSessionId()).isEqualTo(GUEST_ID);
		assertThat(command.getValue().provider()).isEqualTo("openai");
		assertThat(command.getValue().model()).isEqualTo("gpt-test");
		assertThat(command.getValue().promptVersion()).isEqualTo("tarot-prompt-v1");
		verify(repository, never()).createPending(any());
		verify(guestCacheRepository).completePending(any(), any());
	}

	@Test
	void createsUserReadingAsPermanentRecordWithUserOwner() {
		UUID userId = UUID.fromString("3b413be2-2b81-4802-8c6a-f868a85d8d83");
		ConsentService consentService = mock(ConsentService.class);
		ReadingCreationRepository repository = mock(ReadingCreationRepository.class);
		GuestReadingCacheRepository guestCacheRepository = mock(GuestReadingCacheRepository.class);
		when(consentService.getUserStatus(userId)).thenReturn(new ConsentStatus(
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
				"Generating...",
				Map.of("question", "How is today?"),
				null,
				null,
				Instant.parse("2026-06-16T00:00:00Z"),
				Instant.parse("2026-06-16T00:00:00Z")
			)
		));
		when(repository.completePending(any(), any())).thenReturn(completedReading());
		ReadingCreationService service = service(consentService, repository, guestCacheRepository);

		CreatedReadingResponse response = service.createUserReading(
			userId,
			"127.0.0.1",
			REQUEST_ID,
			tarotRequest()
		);

		assertThat(response.status()).isEqualTo("completed");
		ArgumentCaptor<PendingReadingCommand> command =
			ArgumentCaptor.forClass(PendingReadingCommand.class);
		verify(repository).createPending(command.capture());
		assertThat(command.getValue().userId()).isEqualTo(userId);
		assertThat(command.getValue().guestSessionId()).isNull();
		verify(guestCacheRepository, never()).createPending(any());
	}

	@Test
	void rejectsUserReadingWhenUserHasNotAcceptedRequiredConsent() {
		UUID userId = UUID.fromString("3b413be2-2b81-4802-8c6a-f868a85d8d83");
		ConsentService consentService = mock(ConsentService.class);
		ReadingCreationRepository repository = mock(ReadingCreationRepository.class);
		GuestReadingCacheRepository guestCacheRepository = mock(GuestReadingCacheRepository.class);
		when(consentService.getUserStatus(userId)).thenReturn(new ConsentStatus(
			List.of(),
			ConsentDocumentType.required(),
			false
		));
		ReadingCreationService service = service(consentService, repository, guestCacheRepository);

		assertThatThrownBy(() -> service.createUserReading(
			userId,
			"127.0.0.1",
			REQUEST_ID,
			tarotRequest()
		)).isInstanceOf(RequiredConsentMissingException.class);

		verify(repository, never()).createPending(any());
		verify(guestCacheRepository, never()).createPending(any());
	}

	@Test
	void rejectsDuplicateTarotCards() {
		ConsentService consentService = mock(ConsentService.class);
		ReadingCreationRepository repository = mock(ReadingCreationRepository.class);
		GuestReadingCacheRepository guestCacheRepository = mock(GuestReadingCacheRepository.class);
		when(consentService.getStatus(GUEST_ID)).thenReturn(new ConsentStatus(
			ConsentDocumentType.required(),
			ConsentDocumentType.required(),
			true
		));
		ReadingCreationService service = service(consentService, repository, guestCacheRepository);

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
		verify(guestCacheRepository, never()).createPending(any());
	}

	@Test
	void rejectsMissingRequestIdBeforeCreatingPendingReading() {
		ConsentService consentService = mock(ConsentService.class);
		ReadingCreationRepository repository = mock(ReadingCreationRepository.class);
		GuestReadingCacheRepository guestCacheRepository = mock(GuestReadingCacheRepository.class);
		when(consentService.getStatus(GUEST_ID)).thenReturn(new ConsentStatus(
			ConsentDocumentType.required(),
			ConsentDocumentType.required(),
			true
		));
		ReadingCreationService service = service(consentService, repository, guestCacheRepository);

		assertThatThrownBy(() -> service.createGuestReading(
			GUEST_ID,
			"127.0.0.1",
			null,
			tarotRequest()
		)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Request id is required");

		verify(repository, never()).createPending(any());
		verify(guestCacheRepository, never()).createPending(any());
	}

	@Test
	void marksPendingReadingFailedWhenAnyGeneratorFails() {
		ConsentService consentService = mock(ConsentService.class);
		ReadingCreationRepository repository = mock(ReadingCreationRepository.class);
		GuestReadingCacheRepository guestCacheRepository = mock(GuestReadingCacheRepository.class);
		when(consentService.getStatus(GUEST_ID)).thenReturn(new ConsentStatus(
			ConsentDocumentType.required(),
			ConsentDocumentType.required(),
			true
		));
		PendingGuestReadingCache pending = new PendingGuestReadingCache(
			READING_ID,
			new CreatedReadingResponse(
				READING_ID,
				ReadingKind.TAROT,
				"generating",
				"Generating...",
				Map.of("question", "How is today?"),
				null,
				null,
				Instant.parse("2026-06-16T00:00:00Z"),
				Instant.parse("2026-06-16T00:00:00Z")
			)
		);
		when(guestCacheRepository.createPending(any())).thenReturn(pending);
		ReadingGenerator failingGenerator = (kind, question, input) -> {
			throw new IllegalStateException("demo generator failed");
		};
		ReadingCreationService service = new ReadingCreationService(
			consentService,
			repository,
			guestCacheRepository,
			failingGenerator,
			new ObjectMapper(),
			"test-signing-secret",
			metadataResolver()
		);

		assertThatThrownBy(() -> service.createGuestReading(
			GUEST_ID,
			"127.0.0.1",
			REQUEST_ID,
			tarotRequest()
		)).isInstanceOf(IllegalStateException.class);

		verify(guestCacheRepository).failPending(pending, "READING_GENERATION_FAILED");
		verify(guestCacheRepository, never()).completePending(any(), any());
		verify(repository, never()).failPending(any(), any());
	}

	private ReadingCreationService service(
		ConsentService consentService,
		ReadingCreationRepository repository,
		GuestReadingCacheRepository guestCacheRepository
	) {
		return new ReadingCreationService(
			consentService,
			repository,
			guestCacheRepository,
			new DemoReadingGenerator(),
			new ObjectMapper(),
			"test-signing-secret",
			metadataResolver()
		);
	}

	private ReadingGenerationMetadataResolver metadataResolver() {
		return new ReadingGenerationMetadataResolver("gpt-test", "tarot-prompt-v1");
	}

	private CreatedReadingResponse completedReading() {
		return new CreatedReadingResponse(
			READING_ID,
			ReadingKind.TAROT,
			"completed",
			"Tarot reading",
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
