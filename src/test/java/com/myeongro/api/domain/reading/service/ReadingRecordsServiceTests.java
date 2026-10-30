package com.myeongro.api.domain.reading.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
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
import com.myeongro.api.domain.reading.exception.ReadingGenerationInProgressException;
import com.myeongro.api.domain.reading.exception.ReadingRecordNotFoundException;
import com.myeongro.api.domain.reading.repository.ReadingRecordsRepository;
import com.myeongro.api.domain.tarot.model.TarotSpreadType;

class ReadingRecordsServiceTests {

	private static final UUID USER_ID = UUID.fromString("3b413be2-2b81-4802-8c6a-f868a85d8d83");
	private static final UUID READING_ID = UUID.fromString("20e84e95-f5ff-4d9d-a6c4-a3c8ea2e2dfc");

	@Test
	void redactsLegacyFreeTextAndTarotCardsFromGeneratingAndFailedRecords() {
		ReadingRecordsRepository repository = org.mockito.Mockito.mock(ReadingRecordsRepository.class);
		ReadingRecordsService service = new ReadingRecordsService(repository);
		CreatedReadingResponse generating = reading("generating");
		CreatedReadingResponse failed = reading("failed");
		when(repository.listByUser(USER_ID)).thenReturn(List.of(generating, failed));
		when(repository.findByUserAndId(USER_ID, READING_ID)).thenReturn(Optional.of(failed));

		assertThat(service.listByUser(USER_ID))
			.allSatisfy(item -> assertThat(item.input())
				.doesNotContainKeys("question", "choiceOptions", "cards"));
		assertThat(service.getByUserAndId(USER_ID, READING_ID).input())
			.doesNotContainKeys("question", "choiceOptions", "cards");
	}

	@Test
	void returnsCardsButNeverLegacyFreeTextForCompletedRecords() {
		ReadingRecordsRepository repository = org.mockito.Mockito.mock(ReadingRecordsRepository.class);
		ReadingRecordsService service = new ReadingRecordsService(repository);
		CreatedReadingResponse completed = reading("completed");
		when(repository.findByUserAndId(USER_ID, READING_ID)).thenReturn(Optional.of(completed));
		when(repository.listByUser(USER_ID)).thenReturn(List.of(completed));

		assertThat(service.getByUserAndId(USER_ID, READING_ID).input())
			.containsKey("cards")
			.doesNotContainKeys("question", "choiceOptions");
		assertThat(service.getByUserAndId(USER_ID, READING_ID).result()).isSameAs(completed.result());
		assertThat(service.listByUser(USER_ID).getFirst().result()).isSameAs(completed.result());
	}

	@Test
	void refusesToDeleteAReadingThatIsStillGenerating() {
		ReadingRecordsRepository repository = org.mockito.Mockito.mock(ReadingRecordsRepository.class);
		ReadingRecordsService service = new ReadingRecordsService(repository);
		when(repository.deleteByUserAndId(USER_ID, READING_ID)).thenReturn(false);
		when(repository.findByUserAndId(USER_ID, READING_ID))
			.thenReturn(Optional.of(reading("generating")));

		assertThatThrownBy(() -> service.deleteByUserAndId(USER_ID, READING_ID))
			.isInstanceOf(ReadingGenerationInProgressException.class);
	}

	@Test
	void deletesAReadingThatFinishedGeneratingDuringTheFirstAttempt() {
		ReadingRecordsRepository repository = org.mockito.Mockito.mock(ReadingRecordsRepository.class);
		ReadingRecordsService service = new ReadingRecordsService(repository);
		when(repository.deleteByUserAndId(USER_ID, READING_ID)).thenReturn(false, true);
		when(repository.findByUserAndId(USER_ID, READING_ID))
			.thenReturn(Optional.of(reading("completed")));

		service.deleteByUserAndId(USER_ID, READING_ID);

		verify(repository, times(2)).deleteByUserAndId(USER_ID, READING_ID);
	}

	@Test
	void reportsAMissingReadingWhenNothingWasDeleted() {
		ReadingRecordsRepository repository = org.mockito.Mockito.mock(ReadingRecordsRepository.class);
		ReadingRecordsService service = new ReadingRecordsService(repository);
		when(repository.deleteByUserAndId(USER_ID, READING_ID)).thenReturn(false);
		when(repository.findByUserAndId(USER_ID, READING_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.deleteByUserAndId(USER_ID, READING_ID))
			.isInstanceOf(ReadingRecordNotFoundException.class);
	}

	private CreatedReadingResponse reading(String status) {
		return new CreatedReadingResponse(
			READING_ID,
			ReadingKind.TAROT,
			TarotSpreadType.RELATIONSHIP_THREE_CARD.value(),
			ReadingSchemaVersions.TAROT,
			status,
			"관계 리딩",
			Map.of(
				"question", "질문 원문",
				"choiceOptions", Map.of("a", "선택 A", "b", "선택 B"),
				"cards", List.of(card("major-00-fool", "my_heart"))
			),
			Map.of("summary", "원래 요약", "guidance", List.of("과거 제안")),
			"FAILED",
			Instant.parse("2026-06-16T00:00:00Z"),
			Instant.parse("2026-06-16T00:00:01Z")
		);
	}

	private Map<String, Object> card(String cardId, String position) {
		return Map.of("cardId", cardId, "position", position, "reversed", false);
	}
}
