package com.myeongro.api.aipreview;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.myeongro.api.domain.reading.entity.MajorArcana;
import com.myeongro.api.domain.reading.entity.TarotSpreadType;

final class TarotPreviewInputValidator {

	private static final Set<String> CARD_FIELDS = Set.of(
		"cardId", "position", "reversed"
	);
	private static final Set<String> CHOICE_FIELDS = Set.of("a", "b");

	List<String> validate(TarotSpreadType spreadType, Map<String, Object> input) {
		if (spreadType == null) {
			throw new IllegalArgumentException("Tarot preview spread type is required");
		}
		validateInputFields(spreadType, input);
		Object cardsValue = input.get("cards");
		if (!(cardsValue instanceof List<?> cards)
			|| cards.size() != spreadType.cardCount()) {
			throw new IllegalArgumentException(
				spreadType.cardCount() + " tarot preview cards are required for "
					+ spreadType.value()
			);
		}

		List<String> cardIds = new ArrayList<>(cards.size());
		Set<String> uniqueCardIds = new HashSet<>();
		for (int index = 0; index < cards.size(); index++) {
			Object cardValue = cards.get(index);
			if (!(cardValue instanceof Map<?, ?> card)
				|| !card.keySet().equals(CARD_FIELDS)) {
				throw new IllegalArgumentException("Tarot preview card structure is invalid");
			}
			Object cardIdValue = card.get("cardId");
			if (!(cardIdValue instanceof String cardId) || !MajorArcana.contains(cardId)) {
				throw new IllegalArgumentException("Tarot preview card ID is unsupported");
			}
			if (!uniqueCardIds.add(cardId)) {
				throw new IllegalArgumentException("Tarot preview cards must not contain duplicates");
			}
			String expectedPosition = spreadType.positions().get(index).id();
			if (!expectedPosition.equals(card.get("position"))) {
				throw new IllegalArgumentException("Tarot preview card positions are not canonical");
			}
			if (!Boolean.FALSE.equals(card.get("reversed"))) {
				throw new IllegalArgumentException("Tarot preview cards must use reversed=false");
			}
			cardIds.add(cardId);
		}
		validateChoiceOptions(spreadType, input.get("choiceOptions"));
		return List.copyOf(cardIds);
	}

	private void validateInputFields(TarotSpreadType spreadType, Map<String, Object> input) {
		Set<String> expectedFields = spreadType == TarotSpreadType.CHOICE_FIVE_CARD
			? Set.of("cards", "choiceOptions")
			: Set.of("cards");
		if (!input.keySet().equals(expectedFields)) {
			throw new IllegalArgumentException("Tarot preview input structure is invalid");
		}
	}

	private void validateChoiceOptions(TarotSpreadType spreadType, Object value) {
		if (spreadType != TarotSpreadType.CHOICE_FIVE_CARD) {
			return;
		}
		if (!(value instanceof Map<?, ?> options)
			|| !options.keySet().equals(CHOICE_FIELDS)) {
			throw new IllegalArgumentException("Tarot preview choice options are required");
		}
		String optionA = normalizedChoice(options.get("a"), "A");
		String optionB = normalizedChoice(options.get("b"), "B");
		if (optionA.equals(optionB)) {
			throw new IllegalArgumentException("Tarot preview choice options must be different");
		}
	}

	private String normalizedChoice(Object value, String name) {
		if (!(value instanceof String text) || text.isBlank()
			|| !text.equals(text.trim()) || text.length() > 100) {
			throw new IllegalArgumentException(
				"Tarot preview choice option " + name + " must be normalized and at most 100 characters"
			);
		}
		return text;
	}
}
