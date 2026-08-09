package com.myeongro.api.aipreview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.myeongro.api.domain.reading.entity.TarotSpreadType;

class TarotPreviewInputValidatorTests {

	private final TarotPreviewInputValidator validator = new TarotPreviewInputValidator();

	@Test
	void acceptsCanonicalNormalizedTarotCards() {
		List<String> cardIds = validator.validate(
			TarotSpreadType.MIND_THREE_CARD,
			input(
				card("major-02-high-priestess", "emotion", false),
				card("major-00-fool", "underlying_need", false),
				card("major-01-magician", "self_action", false)
			)
		);

		assertThat(cardIds).containsExactly(
			"major-02-high-priestess", "major-00-fool", "major-01-magician"
		);
	}

	@Test
	void rejectsWrongCardCount() {
		assertThatThrownBy(() -> validator.validate(
			TarotSpreadType.MIND_THREE_CARD,
			input(card("major-00-fool", "emotion", false))
		)).hasMessageContaining("3 tarot preview cards");
	}

	@Test
	void rejectsNonCanonicalPositionOrder() {
		assertThatThrownBy(() -> validator.validate(
			TarotSpreadType.MIND_THREE_CARD,
			input(
				card("major-02-high-priestess", "underlying_need", false),
				card("major-00-fool", "emotion", false),
				card("major-01-magician", "self_action", false)
			)
		)).hasMessageContaining("positions are not canonical");
	}

	@Test
	void rejectsUnknownOrDuplicateCards() {
		assertThatThrownBy(() -> validator.validate(
			TarotSpreadType.MIND_THREE_CARD,
			input(
				card("major-99-unknown", "emotion", false),
				card("major-00-fool", "underlying_need", false),
				card("major-01-magician", "self_action", false)
			)
		)).hasMessageContaining("unsupported");

		assertThatThrownBy(() -> validator.validate(
			TarotSpreadType.MIND_THREE_CARD,
			input(
				card("major-00-fool", "emotion", false),
				card("major-00-fool", "underlying_need", false),
				card("major-01-magician", "self_action", false)
			)
		)).hasMessageContaining("duplicates");
	}

	@Test
	void rejectsReversedCards() {
		assertThatThrownBy(() -> validator.validate(
			TarotSpreadType.MIND_THREE_CARD,
			input(
				card("major-02-high-priestess", "emotion", false),
				card("major-00-fool", "underlying_need", true),
				card("major-01-magician", "self_action", false)
			)
		)).hasMessageContaining("reversed=false");
	}

	@SafeVarargs
	private final Map<String, Object> input(Map<String, Object>... cards) {
		return Map.of("cards", List.of(cards));
	}

	private Map<String, Object> card(String cardId, String position, boolean reversed) {
		return Map.of(
			"cardId", cardId,
			"position", position,
			"reversed", reversed
		);
	}
}
