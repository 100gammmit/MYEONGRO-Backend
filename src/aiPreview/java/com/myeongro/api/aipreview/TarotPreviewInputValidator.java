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

	List<String> validate(TarotSpreadType spreadType, Map<String, Object> input) {
		if (spreadType == null) {
			throw new IllegalArgumentException("Tarot preview spread type is required");
		}
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
		return List.copyOf(cardIds);
	}
}
