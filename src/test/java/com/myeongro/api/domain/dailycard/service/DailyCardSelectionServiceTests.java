package com.myeongro.api.domain.dailycard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.myeongro.api.domain.dailycard.config.DailyCardProperties;
import com.myeongro.api.domain.dailycard.exception.DailyCardContentVersionMismatchException;
import com.myeongro.api.domain.dailycard.exception.InvalidDailyCardSelectionException;
import com.myeongro.api.domain.tarot.model.MajorArcana;
import com.myeongro.api.domain.tarot.selection.TarotCardRanker;

class DailyCardSelectionServiceTests {

	private static final String VERSION = "daily-one-card-static-v1";
	private static final UUID DRAW_ID = UUID.fromString(
		"82ed11d5-2269-438c-9815-42e6f13735f4"
	);
	private static final String SECRET = "test-only-tarot-selection-secret-32-bytes";

	@Test
	void returnsCanonicalDeterministicSelectionWithServerKoreanDate() {
		DailyCardSelectionService service = service("2026-08-24T15:00:01Z");

		var first = service.select(DRAW_ID, 3, VERSION).selection();
		var repeated = service.select(DRAW_ID, 3, VERSION).selection();

		assertThat(first).isEqualTo(repeated);
		assertThat(first.dateKst()).hasToString("2026-08-25");
		assertThat(MajorArcana.contains(first.cardId())).isTrue();
		assertThat(first.variantIndex()).isBetween(0, 5);
		assertThat(first.contentVersion()).isEqualTo(VERSION);
	}

	@Test
	void givesFiveSlotsFiveDistinctCards() {
		DailyCardSelectionService service = service("2026-08-25T04:00:00Z");

		assertThat(java.util.stream.IntStream.rangeClosed(1, 5)
			.mapToObj(slot -> service.select(DRAW_ID, slot, VERSION).selection().cardId())
			.toList()).doesNotHaveDuplicates();
	}

	@Test
	void rejectsUnsupportedContentVersion() {
		assertThatThrownBy(() -> service("2026-08-25T04:00:00Z")
			.select(DRAW_ID, 1, "daily-one-card-static-v2"))
			.isInstanceOf(DailyCardContentVersionMismatchException.class);
	}

	@Test
	void rejectsSlotOutsideTheFiveDisplayedCards() {
		assertThatThrownBy(() -> service("2026-08-25T04:00:00Z")
			.select(DRAW_ID, 6, VERSION))
			.isInstanceOf(InvalidDailyCardSelectionException.class);
	}

	private DailyCardSelectionService service(String instant) {
		return new DailyCardSelectionService(
			new TarotCardRanker(SECRET),
			new DailyCardProperties(VERSION, 6),
			Clock.fixed(Instant.parse(instant), ZoneOffset.UTC)
		);
	}
}
