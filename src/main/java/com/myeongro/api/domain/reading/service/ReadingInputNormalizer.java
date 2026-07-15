package com.myeongro.api.domain.reading.service;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.myeongro.api.domain.reading.controller.ChoiceOptionsRequest;
import com.myeongro.api.domain.reading.controller.ReadingCreateRequest;
import com.myeongro.api.domain.reading.dto.CreatedReadingResponse;
import com.myeongro.api.domain.reading.entity.MajorArcana;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.entity.TarotSpreadType;

@Component
public class ReadingInputNormalizer {

	public NormalizedReadingInput normalize(ReadingCreateRequest request) {
		ReadingKind kind = ReadingKind.fromValue(request.kind());
		String question = normalizeText(request.question(), 300, "Question");
		if (kind == ReadingKind.TAROT) {
			return normalizeTarot(request, question);
		}
		return normalizeSaju(request, question);
	}

	public NormalizedReadingInput restore(CreatedReadingResponse reading) {
		if (reading.schemaVersion() != NormalizedReadingInput.CURRENT_SCHEMA_VERSION) {
			throw new IllegalArgumentException("Unsupported reading schema version");
		}
		Map<String, Object> payload = reading.input();
		String question = valueAsString(payload.get("question"), "Stored question");
		if (reading.kind() == ReadingKind.TAROT) {
			TarotSpreadType spread = reading.spreadType();
			if (spread == null) {
				throw new IllegalArgumentException("Stored tarot spread is required");
			}
			List<String> cardIds = storedCardIds(payload, spread);
			ChoiceOptionsRequest choices = storedChoiceOptions(payload, spread);
			return normalize(new ReadingCreateRequest(
				reading.kind().value(),
				spread.value(),
				question,
				java.util.UUID.randomUUID(),
				cardIds,
				choices,
				null,
				null,
				null
			));
		}
		Map<?, ?> profile = valueAsMap(payload.get("profile"), "Stored profile");
		return normalize(new ReadingCreateRequest(
			reading.kind().value(),
			null,
			question,
			java.util.UUID.randomUUID(),
			null,
			null,
			valueAsString(profile.get("birthDate"), "Stored birth date"),
			nullableString(profile.get("birthTime")),
			nullableString(profile.get("gender"))
		));
	}

	private NormalizedReadingInput normalizeTarot(
		ReadingCreateRequest request,
		String question
	) {
		TarotSpreadType spread = TarotSpreadType.fromValue(request.spreadType());
		List<String> cardIds = request.cardIds() == null ? List.of() : request.cardIds();
		if (cardIds.size() != spread.cardCount()) {
			throw new IllegalArgumentException(
				spread.cardCount() + " tarot cards are required for " + spread.value()
			);
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
				return Map.<String, Object>of(
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
			payload.put("choiceOptions", Map.of("a", optionA, "b", optionB));
		} else if (choices != null) {
			throw new IllegalArgumentException("Choice options are only allowed for choice spread");
		}

		return new NormalizedReadingInput(
			ReadingKind.TAROT,
			spread,
			NormalizedReadingInput.CURRENT_SCHEMA_VERSION,
			question,
			Collections.unmodifiableMap(new LinkedHashMap<>(payload))
		);
	}

	private NormalizedReadingInput normalizeSaju(
		ReadingCreateRequest request,
		String question
	) {
		if (request.spreadType() != null || request.cardIds() != null
			|| request.choiceOptions() != null) {
			throw new IllegalArgumentException("Tarot fields are not allowed for saju");
		}
		String birthDate = normalizeText(request.birthDate(), 20, "Birth date");
		String gender = request.gender() == null ? "unspecified" : request.gender();
		if (!Set.of("female", "male", "unspecified").contains(gender)) {
			throw new IllegalArgumentException("Invalid gender");
		}
		return new NormalizedReadingInput(
			ReadingKind.SAJU,
			null,
			NormalizedReadingInput.CURRENT_SCHEMA_VERSION,
			question,
			Map.of(
				"question", question,
				"profile", Map.of(
					"calendarType", "solar",
					"birthDate", birthDate,
					"birthTime", request.birthTime() == null ? "" : request.birthTime(),
					"gender", gender
				)
			)
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

	private List<String> storedCardIds(
		Map<String, Object> payload,
		TarotSpreadType spread
	) {
		if (!(payload.get("cards") instanceof List<?> cards)
			|| cards.size() != spread.cardCount()) {
			throw new IllegalArgumentException("Stored tarot cards do not match spread");
		}
		return java.util.stream.IntStream.range(0, cards.size())
			.mapToObj(index -> {
				Map<?, ?> card = valueAsMap(cards.get(index), "Stored tarot card");
				String position = valueAsString(card.get("position"), "Stored card position");
				if (!spread.positions().get(index).id().equals(position)
					|| !Boolean.FALSE.equals(card.get("reversed"))) {
					throw new IllegalArgumentException("Stored tarot card order is invalid");
				}
				return valueAsString(card.get("cardId"), "Stored tarot card id");
			})
			.toList();
	}

	private ChoiceOptionsRequest storedChoiceOptions(
		Map<String, Object> payload,
		TarotSpreadType spread
	) {
		Object stored = payload.get("choiceOptions");
		if (spread != TarotSpreadType.CHOICE_FIVE_CARD) {
			if (stored != null) {
				throw new IllegalArgumentException("Unexpected stored choice options");
			}
			return null;
		}
		Map<?, ?> choices = valueAsMap(stored, "Stored choice options");
		return new ChoiceOptionsRequest(
			valueAsString(choices.get("a"), "Stored choice option A"),
			valueAsString(choices.get("b"), "Stored choice option B")
		);
	}

	private Map<?, ?> valueAsMap(Object value, String fieldName) {
		if (value instanceof Map<?, ?> map) {
			return map;
		}
		throw new IllegalArgumentException(fieldName + " is invalid");
	}

	private String valueAsString(Object value, String fieldName) {
		if (value instanceof String text && !text.isBlank()) {
			return text;
		}
		throw new IllegalArgumentException(fieldName + " is invalid");
	}

	private String nullableString(Object value) {
		return value instanceof String text ? text : null;
	}
}
