package com.myeongro.api.domain.tarot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.myeongro.api.domain.tarot.model.MajorArcana;
import com.myeongro.api.domain.tarot.model.TarotSpreadType;
import com.myeongro.api.domain.tarot.selection.TarotCardRanker;

class TarotCardSelectorTests {

	private static final UUID USER_ID = UUID.fromString(
		"3b413be2-2b81-4802-8c6a-f868a85d8d83"
	);
	private static final UUID REQUEST_ID = UUID.fromString(
		"82ed11d5-2269-438c-9815-42e6f13735f4"
	);
	private final TarotCardSelector selector = new TarotCardSelector(
		new TarotCardRanker("test-only-tarot-selection-secret-32-bytes")
	);

	@ParameterizedTest
	@EnumSource(TarotSpreadType.class)
	void selectsUniqueCanonicalCardsForEverySpread(TarotSpreadType spread) {
		List<Integer> slots = java.util.Collections.nCopies(spread.cardCount(), 3);

		List<String> cards = selector.select(USER_ID, REQUEST_ID, spread, slots);

		assertThat(cards).hasSize(spread.cardCount()).doesNotHaveDuplicates();
		assertThat(cards).allMatch(MajorArcana::contains);
	}

	@Test
	void returnsTheSameCardsForTheSameSelectionContext() {
		List<Integer> slots = List.of(5, 1, 3, 2, 4);

		assertThat(selector.select(
			USER_ID, REQUEST_ID, TarotSpreadType.CHOICE_FIVE_CARD, slots
		)).isEqualTo(selector.select(
			USER_ID, REQUEST_ID, TarotSpreadType.CHOICE_FIVE_CARD, slots
		));
	}

	@Test
	void preservesSlotOrderAsMeaningfulInput() {
		assertThat(selector.select(
			USER_ID, REQUEST_ID, TarotSpreadType.MIND_THREE_CARD, List.of(1, 2, 3)
		)).isNotEqualTo(selector.select(
			USER_ID, REQUEST_ID, TarotSpreadType.MIND_THREE_CARD, List.of(3, 2, 1)
		));
	}

	@Test
	void rejectsMissingWrongLengthNullAndOutOfRangeSlots() {
		assertThatThrownBy(() -> selector.select(
			USER_ID, REQUEST_ID, TarotSpreadType.MIND_THREE_CARD, null
		)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> selector.select(
			USER_ID, REQUEST_ID, TarotSpreadType.MIND_THREE_CARD, List.of(1)
		)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> selector.select(
			USER_ID, REQUEST_ID, TarotSpreadType.MIND_THREE_CARD,
			java.util.Arrays.asList(1, null, 3)
		)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> selector.select(
			USER_ID, REQUEST_ID, TarotSpreadType.MIND_THREE_CARD, List.of(0, 1, 6)
		)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsShortSecrets() {
		assertThatThrownBy(() -> new TarotCardRanker("too-short"))
			.isInstanceOf(IllegalArgumentException.class);
	}
}
