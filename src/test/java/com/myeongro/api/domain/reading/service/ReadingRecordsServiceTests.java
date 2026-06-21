package com.myeongro.api.domain.reading.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.myeongro.api.domain.reading.dto.CreatedReadingResponse;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.exception.ReadingRecordNotFoundException;
import com.myeongro.api.domain.reading.repository.PendingReadingCreation;
import com.myeongro.api.domain.reading.repository.ReadingRecordsRepository;

class ReadingRecordsServiceTests {

	private static final UUID USER_ID =
		UUID.fromString("3b413be2-2b81-4802-8c6a-f868a85d8d83");
	private static final UUID READING_ID =
		UUID.fromString("20e84e95-f5ff-4d9d-a6c4-a3c8ea2e2dfc");
	private static final UUID GENERATION_ID =
		UUID.fromString("75a85049-44cb-4147-9f73-677a8d9137d7");

	@Test
	void returnsReadingDetailOwnedByUser() {
		ReadingRecordsRepository repository = mock(ReadingRecordsRepository.class);
		ReadingRecordsService service = service(repository, mock(ReadingCreationService.class));
		when(repository.findByUserAndId(USER_ID, READING_ID)).thenReturn(Optional.of(reading()));

		CreatedReadingResponse response = service.getByUserAndId(USER_ID, READING_ID);

		assertThat(response.id()).isEqualTo(READING_ID);
	}

	@Test
	void throwsNotFoundWhenReadingDoesNotBelongToUser() {
		ReadingRecordsRepository repository = mock(ReadingRecordsRepository.class);
		ReadingRecordsService service = service(repository, mock(ReadingCreationService.class));
		when(repository.findByUserAndId(USER_ID, READING_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getByUserAndId(USER_ID, READING_ID))
			.isInstanceOf(ReadingRecordNotFoundException.class);
	}

	@Test
	void throwsNotFoundWhenSoftDeleteAffectsNoRows() {
		ReadingRecordsRepository repository = mock(ReadingRecordsRepository.class);
		ReadingRecordsService service = service(repository, mock(ReadingCreationService.class));
		when(repository.softDeleteByUserAndId(USER_ID, READING_ID)).thenReturn(false);

		assertThatThrownBy(() -> service.deleteByUserAndId(USER_ID, READING_ID))
			.isInstanceOf(ReadingRecordNotFoundException.class);
	}

	@Test
	void retriesFailedReadingThroughGenerationPipeline() {
		ReadingRecordsRepository repository = mock(ReadingRecordsRepository.class);
		ReadingCreationService creationService = mock(ReadingCreationService.class);
		ReadingRecordsService service = service(repository, creationService);
		PendingReadingCreation pending = new PendingReadingCreation(
			READING_ID,
			GENERATION_ID,
			reading()
		);
		when(repository.startFailedRetry(USER_ID, READING_ID, metadata())).thenReturn(pending);
		when(creationService.generatePending(
			ReadingKind.TAROT,
			"How is today?",
			reading().input(),
			pending
		)).thenReturn(completedReading());

		CreatedReadingResponse response = service.retry(USER_ID, READING_ID);

		assertThat(response.status()).isEqualTo("completed");
		verify(creationService).generatePending(
			ReadingKind.TAROT,
			"How is today?",
			reading().input(),
			pending
		);
	}

	private ReadingRecordsService service(
		ReadingRecordsRepository repository,
		ReadingCreationService creationService
	) {
		return new ReadingRecordsService(repository, creationService, metadata());
	}

	private ReadingGenerationMetadata metadata() {
		return new ReadingGenerationMetadata("demo", "deterministic-demo", "mvp-test");
	}

	private CreatedReadingResponse reading() {
		return new CreatedReadingResponse(
			READING_ID,
			ReadingKind.TAROT,
			"failed",
			"Generating...",
			Map.of("question", "How is today?"),
			null,
			"READING_GENERATION_FAILED",
			Instant.parse("2026-06-16T00:00:00Z"),
			Instant.parse("2026-06-16T00:00:01Z")
		);
	}

	private CreatedReadingResponse completedReading() {
		return new CreatedReadingResponse(
			READING_ID,
			ReadingKind.TAROT,
			"completed",
			"Tarot reading",
			Map.of("question", "How is today?"),
			Map.of(
				"title", "Tarot reading",
				"summary", "The selected cards point to a clear next step.",
				"sections", List.of(Map.of(
					"heading", "Flow",
					"body", "Move gently and choose one concrete action."
				)),
				"guidance", List.of("Choose one next action."),
				"disclaimer", "For reflection only."
			),
			null,
			Instant.parse("2026-06-16T00:00:00Z"),
			Instant.parse("2026-06-16T00:00:02Z")
		);
	}
}
