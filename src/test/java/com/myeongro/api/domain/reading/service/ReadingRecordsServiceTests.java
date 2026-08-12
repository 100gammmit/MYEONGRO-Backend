package com.myeongro.api.domain.reading.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.myeongro.api.domain.reading.dto.CreatedReadingResponse;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.entity.TarotSpreadType;
import com.myeongro.api.domain.reading.repository.PendingReadingCreation;
import com.myeongro.api.domain.reading.repository.ReadingRecordsRepository;
import com.myeongro.api.domain.reading.exception.ReadingRetryNotAllowedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.saju.place.SajuBirthPlaceCatalog;
import com.myeongro.api.domain.saju.service.SajuReadingInputAssembler;
import org.springframework.core.io.ClassPathResource;

class ReadingRecordsServiceTests {

	private static final UUID USER_ID = UUID.fromString("3b413be2-2b81-4802-8c6a-f868a85d8d83");
	private static final UUID READING_ID = UUID.fromString("20e84e95-f5ff-4d9d-a6c4-a3c8ea2e2dfc");

	@Test
	void redactsTarotCardsFromGeneratingAndFailedPublicRecords() {
		ReadingRecordsRepository repository = org.mockito.Mockito.mock(ReadingRecordsRepository.class);
		ReadingRecordsService service = service(repository);
		CreatedReadingResponse generating = reading(1, "generating");
		CreatedReadingResponse failed = reading(1, "failed");
		when(repository.listByUser(USER_ID)).thenReturn(List.of(generating, failed));
		when(repository.findByUserAndId(USER_ID, READING_ID)).thenReturn(Optional.of(failed));

		assertThat(service.listByUser(USER_ID))
			.allSatisfy(item -> assertThat(item.input())
				.containsKey("question")
				.doesNotContainKey("cards"));
		assertThat(service.getByUserAndId(USER_ID, READING_ID).input())
			.doesNotContainKey("cards");
	}

	@Test
	void keepsTarotCardsInCompletedPublicRecords() {
		ReadingRecordsRepository repository = org.mockito.Mockito.mock(ReadingRecordsRepository.class);
		ReadingRecordsService service = service(repository);
		CreatedReadingResponse completed = reading(1, "completed");
		when(repository.findByUserAndId(USER_ID, READING_ID)).thenReturn(Optional.of(completed));

		assertThat(service.getByUserAndId(USER_ID, READING_ID).input())
			.containsKey("cards");
	}

	@Test
	void repeatedRetryReturnsCompletedReadingAfterResponseLoss() {
		ReadingRecordsRepository repository = org.mockito.Mockito.mock(ReadingRecordsRepository.class);
		ReadingCreationService creationService = org.mockito.Mockito.mock(ReadingCreationService.class);
		ReadingRecordsService service = new ReadingRecordsService(
			repository,
			creationService,
			org.mockito.Mockito.mock(ReadingGenerationMetadataResolver.class),
			org.mockito.Mockito.mock(ReadingInputNormalizer.class),
			org.mockito.Mockito.mock(SajuReadingInputAssembler.class)
		);
		CreatedReadingResponse completed = reading(1, "completed");
		when(repository.findByUserAndId(USER_ID, READING_ID)).thenReturn(Optional.of(completed));

		assertThat(service.retry(USER_ID, READING_ID)).isSameAs(completed);

		org.mockito.Mockito.verifyNoInteractions(creationService);
		verify(repository, org.mockito.Mockito.never()).startFailedRetry(
			org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any()
		);
	}

	@Test
	void retriesWithStoredSpreadSchemaAndPayload() {
		ReadingRecordsRepository repository = org.mockito.Mockito.mock(ReadingRecordsRepository.class);
		ReadingCreationService creationService = org.mockito.Mockito.mock(ReadingCreationService.class);
		ReadingGenerationMetadataResolver metadataResolver =
			org.mockito.Mockito.mock(ReadingGenerationMetadataResolver.class);
		ReadingInputNormalizer normalizer = org.mockito.Mockito.mock(ReadingInputNormalizer.class);
		SajuReadingInputAssembler assembler = org.mockito.Mockito.mock(SajuReadingInputAssembler.class);
		ReadingRecordsService service = new ReadingRecordsService(
			repository, creationService, metadataResolver, normalizer, assembler
		);
		CreatedReadingResponse reading = reading(1, "failed");
		NormalizedReadingInput input = new NormalizedReadingInput(
			ReadingKind.TAROT,
			TarotSpreadType.RELATIONSHIP_THREE_CARD,
			1,
			"질문",
			reading.input(),
			reading.input()
		);
		ReadingGenerationMetadata metadata = new ReadingGenerationMetadata(
			"openai", "gpt-test", "relationship-v1"
		);
		PendingReadingCreation pending = new PendingReadingCreation(READING_ID, 42L, reading);
		when(repository.findByUserAndId(USER_ID, READING_ID)).thenReturn(Optional.of(reading));
		when(normalizer.restore(reading)).thenReturn(input);
		when(assembler.restore(input, reading.input())).thenReturn(input);
		when(metadataResolver.resolve(input.kind(), input.spreadType())).thenReturn(metadata);
		when(repository.startFailedRetry(USER_ID, READING_ID, metadata)).thenReturn(pending);

		service.retry(USER_ID, READING_ID);

		verify(creationService).generatePending(input, pending);
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 2})
	void rejectsUnsupportedTarotSchemaBeforeStartingRetry(int schemaVersion) {
		ReadingRecordsRepository repository = org.mockito.Mockito.mock(ReadingRecordsRepository.class);
		ReadingInputNormalizer normalizer = new ReadingInputNormalizer(
			new SajuBirthPlaceCatalog(
				new ObjectMapper(),
				new ClassPathResource("saju/birth-places/kr-admin-v1.json")
			)
		);
		ReadingRecordsService service = new ReadingRecordsService(
			repository,
			org.mockito.Mockito.mock(ReadingCreationService.class),
			org.mockito.Mockito.mock(ReadingGenerationMetadataResolver.class),
			normalizer,
			org.mockito.Mockito.mock(SajuReadingInputAssembler.class)
		);
		CreatedReadingResponse legacy = reading(schemaVersion, "failed");
		when(repository.findByUserAndId(USER_ID, READING_ID)).thenReturn(Optional.of(legacy));

		assertThatThrownBy(() -> service.retry(USER_ID, READING_ID))
			.isInstanceOf(ReadingRetryNotAllowedException.class);
	}

	private ReadingRecordsService service(ReadingRecordsRepository repository) {
		return new ReadingRecordsService(
			repository,
			org.mockito.Mockito.mock(ReadingCreationService.class),
			org.mockito.Mockito.mock(ReadingGenerationMetadataResolver.class),
			org.mockito.Mockito.mock(ReadingInputNormalizer.class),
			org.mockito.Mockito.mock(SajuReadingInputAssembler.class)
		);
	}

	private CreatedReadingResponse reading(int schemaVersion, String status) {
		return new CreatedReadingResponse(
			READING_ID,
			ReadingKind.TAROT,
			schemaVersion == 0 ? null : TarotSpreadType.RELATIONSHIP_THREE_CARD,
			schemaVersion,
			status,
			"Generating...",
			Map.of(
				"question", "질문",
				"cards", List.of(
					card("major-00-fool", "my_heart"),
					card("major-06-lovers", "relationship_flow"),
					card("major-17-star", "check_point")
				)
			),
			null,
			"FAILED",
			Instant.parse("2026-06-16T00:00:00Z"),
			Instant.parse("2026-06-16T00:00:01Z")
		);
	}

	private Map<String, Object> card(String cardId, String position) {
		return Map.of("cardId", cardId, "position", position, "reversed", false);
	}
}
