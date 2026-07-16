package com.myeongro.api.domain.reading.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

import com.myeongro.api.domain.reading.controller.ChoiceOptionsRequest;
import com.myeongro.api.domain.reading.controller.ReadingCreateRequest;
import com.myeongro.api.domain.reading.entity.TarotSpreadType;

class ReadingInputNormalizerTests {

	private final ReadingInputNormalizer normalizer = new ReadingInputNormalizer();

	@ParameterizedTest
	@MethodSource("spreadRequests")
	void assignsOrderedCardIdsToCanonicalPositions(
		TarotSpreadType spread,
		List<String> cards,
		ChoiceOptionsRequest choices
	) {
		NormalizedReadingInput normalized = normalizer.normalizeTarot(
			request(spread, choices), spread, cards
		);

		assertThat(normalized.spreadType()).isEqualTo(spread);
		assertThat(normalized.schemaVersion()).isEqualTo(1);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> storedCards =
			(List<Map<String, Object>>) normalized.payload().get("cards");
		assertThat(storedCards).extracting(card -> card.get("cardId"))
			.containsExactlyElementsOf(cards);
		assertThat(storedCards).extracting(card -> card.get("position"))
			.containsExactlyElementsOf(spread.positions().stream()
				.map(position -> position.id())
				.toList());
		assertThat(storedCards).allSatisfy(card ->
			assertThat(card).containsEntry("reversed", false));
		assertThat(normalized.hashMaterial().keySet()).containsExactly(
			"kind", "spreadType", "schemaVersion", "inputPayload"
		);
	}

	@Test
	void acceptsChoiceOptionsOnlyForChoiceSpread() {
		assertThatThrownBy(() -> normalizer.normalizeTarot(request(
			TarotSpreadType.DAILY_ONE_CARD,
			new ChoiceOptionsRequest("A", "B")
		), TarotSpreadType.DAILY_ONE_CARD, List.of("major-00-fool")))
			.isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> normalizer.normalizeTarot(request(
			TarotSpreadType.CHOICE_FIVE_CARD,
			null
		), TarotSpreadType.CHOICE_FIVE_CARD, cards(5)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsWrongCardCountDuplicateAndUnknownCards() {
		assertThatThrownBy(() -> normalizer.normalizeTarot(request(
			TarotSpreadType.MIND_THREE_CARD,
			null
		), TarotSpreadType.MIND_THREE_CARD, cards(1)))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> normalizer.normalizeTarot(request(
			TarotSpreadType.MIND_THREE_CARD,
			null
		), TarotSpreadType.MIND_THREE_CARD,
			List.of("major-00-fool", "major-00-fool", "major-01-magician")))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> normalizer.normalizeTarot(request(
			TarotSpreadType.DAILY_ONE_CARD,
			null
		), TarotSpreadType.DAILY_ONE_CARD, List.of("not-a-card")))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> normalizer.normalizeTarot(request(
			TarotSpreadType.DAILY_ONE_CARD,
			null
		), TarotSpreadType.DAILY_ONE_CARD, java.util.Arrays.asList((String) null)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Tarot card is required");
	}

	static java.util.stream.Stream<Arguments> spreadRequests() {
		return java.util.stream.Stream.of(
			Arguments.of(TarotSpreadType.DAILY_ONE_CARD, cards(1), null),
			Arguments.of(TarotSpreadType.MIND_THREE_CARD, cards(3), null),
			Arguments.of(TarotSpreadType.RELATIONSHIP_THREE_CARD, cards(3), null),
			Arguments.of(
				TarotSpreadType.CHOICE_FIVE_CARD,
				cards(5),
				new ChoiceOptionsRequest("현재 일을 유지한다", "새 기회를 준비한다")
			)
		);
	}

	private ReadingCreateRequest request(
		TarotSpreadType spread,
		ChoiceOptionsRequest choices
	) {
		return new ReadingCreateRequest(
			"tarot",
			spread.value(),
			" 질문 ",
			UUID.randomUUID(),
			"draw-session-id",
			choices,
			null,
			null,
			null
		);
	}

	private static List<String> cards(int count) {
		return List.of(
			"major-00-fool",
			"major-01-magician",
			"major-02-high-priestess",
			"major-03-empress",
			"major-04-emperor"
		).subList(0, count);
	}
}
