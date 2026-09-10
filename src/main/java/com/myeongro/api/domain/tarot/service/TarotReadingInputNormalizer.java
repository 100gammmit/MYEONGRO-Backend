package com.myeongro.api.domain.tarot.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.service.NormalizedReadingInput;
import com.myeongro.api.domain.reading.service.ReadingInputSupport;
import com.myeongro.api.domain.reading.service.ReadingSchemaVersions;
import com.myeongro.api.domain.tarot.controller.ChoiceOptionsRequest;
import com.myeongro.api.domain.tarot.controller.TarotReadingCreateRequest;
import com.myeongro.api.domain.tarot.model.MajorArcana;
import com.myeongro.api.domain.tarot.model.TarotSpreadType;

@Component
public class TarotReadingInputNormalizer {

	public NormalizedReadingInput normalize(
		TarotReadingCreateRequest request,
		TarotSpreadType spread,
		List<String> cardIds
	) {
		if (spread != TarotSpreadType.fromValue(request.spreadType())) {
			throw ReadingInputSupport.invalid(
				"INVALID_SPREAD_TYPE",
				"spreadType",
				"타로 배열이 선택 결과와 일치하지 않습니다."
			);
		}
		return normalize(request, ReadingInputSupport.normalizeQuestion(request.question()), spread, cardIds);
	}

	private NormalizedReadingInput normalize(
		TarotReadingCreateRequest request,
		String question,
		TarotSpreadType spread,
		List<String> cardIds
	) {
		if (cardIds.size() != spread.cardCount()) {
			throw new IllegalArgumentException(
				spread.cardCount() + " tarot cards are required for " + spread.value()
			);
		}
		if (cardIds.stream().anyMatch(java.util.Objects::isNull)) {
			throw new IllegalArgumentException("Tarot card is required");
		}
		if (Set.copyOf(cardIds).size() != cardIds.size()) {
			throw new IllegalArgumentException("Duplicate tarot card");
		}
		if (cardIds.stream().anyMatch(cardId -> !MajorArcana.contains(cardId))) {
			throw new IllegalArgumentException("Unknown tarot card");
		}

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("question", question);
		payload.put("cards", spread.positions().stream()
			.map(position -> {
				int index = spread.positions().indexOf(position);
				return ReadingInputSupport.orderedMap(
					"cardId", cardIds.get(index),
					"position", position.id(),
					"reversed", false
				);
			})
			.toList());

		ChoiceOptionsRequest choices = request.choiceOptions();
		if (spread == TarotSpreadType.CHOICE_FIVE_CARD) {
			if (choices == null) {
				throw new IllegalArgumentException("Choice options are required");
			}
			String optionA = normalizeText(choices.a(), 100, "Choice option A");
			String optionB = normalizeText(choices.b(), 100, "Choice option B");
			if (optionA.equals(optionB)) {
				throw new IllegalArgumentException("Choice options must be different");
			}
			payload.put("choiceOptions", ReadingInputSupport.orderedMap("a", optionA, "b", optionB));
		} else if (choices != null) {
			throw new IllegalArgumentException("Choice options are only allowed for choice spread");
		}

		Map<String, Object> immutablePayload = ReadingInputSupport.immutable(payload);
		return new NormalizedReadingInput(
			ReadingKind.TAROT,
			spread.value(),
			ReadingSchemaVersions.TAROT,
			question,
			immutablePayload
		);
	}

	private String normalizeText(String value, int maxLength, String fieldName) {
		if (value == null || value.trim().isEmpty()) {
			throw new IllegalArgumentException(fieldName + " is required");
		}
		String normalized = value.trim();
		if (normalized.length() > maxLength) {
			throw new IllegalArgumentException(fieldName + " is too long");
		}
		return normalized;
	}
}
