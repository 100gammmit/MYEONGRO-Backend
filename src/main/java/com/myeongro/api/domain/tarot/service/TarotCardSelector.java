package com.myeongro.api.domain.tarot.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.myeongro.api.domain.tarot.model.MajorArcana;
import com.myeongro.api.domain.tarot.model.TarotSpreadType;
import com.myeongro.api.domain.tarot.selection.TarotCardRanker;

@Component
public class TarotCardSelector {

	private static final int CANDIDATE_COUNT = 5;
	private final TarotCardRanker cardRanker;

	public TarotCardSelector(TarotCardRanker cardRanker) {
		this.cardRanker = cardRanker;
	}

	public List<String> select(
		UUID userId,
		UUID requestId,
		TarotSpreadType spread,
		List<Integer> selectedSlots
	) {
		requireSelection(userId, requestId, spread, selectedSlots);

		List<String> remaining = new ArrayList<>(MajorArcana.all());
		List<String> selected = new ArrayList<>(spread.cardCount());
		for (int positionIndex = 0; positionIndex < spread.cardCount(); positionIndex++) {
			remaining = new ArrayList<>(cardRanker.rank(
				"tarot-selection-v1",
				List.of(
					userId.toString(),
					requestId.toString(),
					spread.value(),
					Integer.toString(positionIndex)
				),
				remaining
			));
			String cardId = remaining.get(selectedSlots.get(positionIndex) - 1);
			selected.add(cardId);
			remaining.remove(cardId);
		}
		return List.copyOf(selected);
	}

	private void requireSelection(
		UUID userId,
		UUID requestId,
		TarotSpreadType spread,
		List<Integer> selectedSlots
	) {
		if (userId == null || requestId == null || spread == null) {
			throw new IllegalArgumentException("Tarot selection context is required");
		}
		if (selectedSlots == null || selectedSlots.size() != spread.cardCount()) {
			throw new IllegalArgumentException(
				spread.cardCount() + " selected slots are required for " + spread.value()
			);
		}
		if (selectedSlots.stream().anyMatch(
			slot -> slot == null || slot < 1 || slot > CANDIDATE_COUNT
		)) {
			throw new IllegalArgumentException("Each selected slot must be between 1 and 5");
		}
	}

}
