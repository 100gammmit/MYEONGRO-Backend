package com.myeongro.api.domain.reading.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class TarotSpreadTypeTests {

	@Test
	void ownsTheFrozenPublicSpreadContract() {
		assertSpread(TarotSpreadType.DAILY_ONE_CARD, "daily_one_card", "today");
		assertSpread(
			TarotSpreadType.MIND_THREE_CARD,
			"mind_three_card",
			"emotion", "underlying_need", "self_action"
		);
		assertSpread(
			TarotSpreadType.RELATIONSHIP_THREE_CARD,
			"relationship_three_card",
			"my_heart", "relationship_flow", "check_point"
		);
		assertSpread(
			TarotSpreadType.CHOICE_FIVE_CARD,
			"choice_five_card",
			"desire", "fear", "core_value", "option_a", "option_b"
		);
	}

	private void assertSpread(
		TarotSpreadType spread,
		String value,
		String... positions
	) {
		assertThat(spread.value()).isEqualTo(value);
		assertThat(spread.cardCount()).isEqualTo(positions.length);
		assertThat(spread.positions()).extracting(TarotPosition::id)
			.containsExactly(positions);
	}
}
