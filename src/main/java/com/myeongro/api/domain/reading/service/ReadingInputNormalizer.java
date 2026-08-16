package com.myeongro.api.domain.reading.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.myeongro.api.domain.reading.controller.ChoiceOptionsRequest;
import com.myeongro.api.domain.reading.controller.SajuReadingCreateRequest;
import com.myeongro.api.domain.reading.controller.TarotReadingCreateRequest;
import com.myeongro.api.domain.reading.dto.CreatedReadingResponse;
import com.myeongro.api.domain.reading.entity.MajorArcana;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.entity.TarotSpreadType;
import com.myeongro.api.domain.reading.exception.InvalidReadingRequestException;
import com.myeongro.api.domain.saju.model.BirthTimePrecision;
import com.myeongro.api.domain.saju.model.LuckDirectionBasis;
import com.myeongro.api.domain.saju.model.SajuBirthProfileRequest;
import com.myeongro.api.domain.saju.model.SajuFocusArea;
import com.myeongro.api.domain.saju.place.SajuBirthPlaceCatalog;

@Component
public class ReadingInputNormalizer {

	private static final int MINIMUM_BIRTH_YEAR = 1900;
	private static final int MAXIMUM_BIRTH_YEAR = 2099;

	private final SajuBirthPlaceCatalog birthPlaceCatalog;

	public ReadingInputNormalizer(SajuBirthPlaceCatalog birthPlaceCatalog) {
		this.birthPlaceCatalog = birthPlaceCatalog;
	}

	public NormalizedReadingInput normalizeTarot(
		TarotReadingCreateRequest request,
		TarotSpreadType spread,
		List<String> cardIds
	) {
		if (spread != TarotSpreadType.fromValue(request.spreadType())) {
			throw invalid(
				"INVALID_SPREAD_TYPE",
				"spreadType",
				"타로 배열이 선택 결과와 일치하지 않습니다."
			);
		}
		return normalizeTarot(request, normalizeQuestion(request.question()), spread, cardIds);
	}

	public NormalizedReadingInput restore(CreatedReadingResponse reading) {
		if (!ReadingSchemaVersions.supports(reading.kind(), reading.schemaVersion())) {
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
			return normalizeTarot(new TarotReadingCreateRequest(
				spread.value(),
				question,
				java.util.UUID.randomUUID(),
				null,
				choices
			), spread, cardIds);
		}
		Map<?, ?> profile = valueAsMap(payload.get("birthProfile"), "Stored birth profile");
		return normalizeSaju(new SajuReadingCreateRequest(
			question,
			java.util.UUID.randomUUID(),
			new SajuBirthProfileRequest(
				valueAsString(profile.get("calendarType"), "Stored calendar type"),
				valueAsString(profile.get("birthDate"), "Stored birth date"),
				nullableString(profile.get("birthTime")),
				valueAsString(profile.get("birthTimePrecision"), "Stored birth time precision"),
				valueAsString(profile.get("provinceCode"), "Stored province code"),
				valueAsString(profile.get("cityCode"), "Stored city code"),
				valueAsString(profile.get("luckDirectionBasis"), "Stored luck direction basis")
			),
			valueAsString(payload.get("focusArea"), "Stored focus area")
		));
	}

	private NormalizedReadingInput normalizeTarot(
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
				return orderedMap(
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
			payload.put("choiceOptions", orderedMap("a", optionA, "b", optionB));
		} else if (choices != null) {
			throw new IllegalArgumentException("Choice options are only allowed for choice spread");
		}

		Map<String, Object> immutablePayload = immutable(payload);
		return new NormalizedReadingInput(
			ReadingKind.TAROT,
			spread,
			ReadingSchemaVersions.TAROT,
			question,
			immutablePayload,
			hashMaterial(ReadingKind.TAROT, spread, ReadingSchemaVersions.TAROT, immutablePayload)
		);
	}

	public NormalizedReadingInput normalizeSaju(SajuReadingCreateRequest request) {
		String question = normalizeQuestion(request.question());
		SajuBirthProfileRequest profile = request.birthProfile();
		if (profile == null) {
			throw invalid("INVALID_BIRTH_DATE", "birthProfile", "출생정보를 입력해 주세요.");
		}
		if (!"solar".equals(profile.calendarType())) {
			throw invalid(
				"UNSUPPORTED_CALENDAR_TYPE",
				"birthProfile.calendarType",
				"현재는 양력 생년월일만 지원합니다."
			);
		}

		LocalDate birthDate = parseBirthDate(profile.birthDate());
		BirthTimePrecision precision = BirthTimePrecision.fromValue(profile.birthTimePrecision());
		String birthTime = normalizeBirthTime(profile.birthTime(), precision);
		LuckDirectionBasis luckDirectionBasis = LuckDirectionBasis.fromValue(
			profile.luckDirectionBasis()
		);
		SajuFocusArea focusArea = SajuFocusArea.fromValue(request.focusArea());
		birthPlaceCatalog.require(profile.provinceCode(), profile.cityCode());

		Map<String, Object> normalizedProfile = new LinkedHashMap<>();
		normalizedProfile.put("calendarType", "solar");
		normalizedProfile.put("birthDate", birthDate.toString());
		if (birthTime != null) {
			normalizedProfile.put("birthTime", birthTime);
		}
		normalizedProfile.put("birthTimePrecision", precision.value());
		normalizedProfile.put("provinceCode", profile.provinceCode());
		normalizedProfile.put("cityCode", profile.cityCode());
		normalizedProfile.put("luckDirectionBasis", luckDirectionBasis.value());

		Map<String, Object> payload = orderedMap(
			"question", question,
			"focusArea", focusArea.value(),
			"birthProfile", immutable(normalizedProfile)
		);
		return new NormalizedReadingInput(
			ReadingKind.SAJU,
			null,
			ReadingSchemaVersions.SAJU,
			question,
			payload,
			hashMaterial(ReadingKind.SAJU, null, ReadingSchemaVersions.SAJU, payload)
		);
	}

	private LocalDate parseBirthDate(String value) {
		try {
			LocalDate date = LocalDate.parse(value);
			if (date.getYear() < MINIMUM_BIRTH_YEAR || date.getYear() > MAXIMUM_BIRTH_YEAR) {
				throw invalid(
					"UNSUPPORTED_BIRTH_YEAR",
					"birthProfile.birthDate",
					"출생 연도는 1900년부터 2099년까지 입력할 수 있습니다."
				);
			}
			return date;
		} catch (DateTimeParseException | NullPointerException exception) {
			throw invalid(
				"INVALID_BIRTH_DATE",
				"birthProfile.birthDate",
				"생년월일을 확인해 주세요."
			);
		}
	}

	private String normalizeBirthTime(String value, BirthTimePrecision precision) {
		boolean missing = value == null || value.isBlank();
		if (precision == BirthTimePrecision.UNKNOWN) {
			if (!missing) {
				throw invalid(
					"INVALID_BIRTH_TIME",
					"birthProfile.birthTime",
					"출생시간을 모르는 경우 시각을 비워 주세요."
				);
			}
			return null;
		}
		if (missing || !value.matches("\\d{2}:\\d{2}")) {
			throw invalid(
				"INVALID_BIRTH_TIME",
				"birthProfile.birthTime",
				"출생시간을 시와 분으로 입력해 주세요."
			);
		}
		try {
			return LocalTime.parse(value).toString();
		} catch (DateTimeParseException exception) {
			throw invalid(
				"INVALID_BIRTH_TIME",
				"birthProfile.birthTime",
				"출생시간을 확인해 주세요."
			);
		}
	}

	private String normalizeQuestion(String value) {
		if (value == null || value.trim().isEmpty()) {
			throw invalid("QUESTION_REQUIRED", "question", "궁금한 점을 입력해 주세요.");
		}
		String normalized = value.trim();
		if (normalized.length() > 300) {
			throw invalid("QUESTION_TOO_LONG", "question", "질문은 300자 이하로 입력해 주세요.");
		}
		return normalized;
	}

	private Map<String, Object> hashMaterial(
		ReadingKind kind,
		TarotSpreadType spread,
		int schemaVersion,
		Map<String, Object> payload
	) {
		return orderedMap(
			"kind", kind.value(),
			"spreadType", spread == null ? null : spread.value(),
			"schemaVersion", schemaVersion,
			"inputPayload", payload
		);
	}

	private Map<String, Object> orderedMap(Object... entries) {
		Map<String, Object> values = new LinkedHashMap<>();
		for (int index = 0; index < entries.length; index += 2) {
			values.put((String) entries[index], entries[index + 1]);
		}
		return immutable(values);
	}

	private Map<String, Object> immutable(Map<String, Object> values) {
		return Collections.unmodifiableMap(new LinkedHashMap<>(values));
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

	private InvalidReadingRequestException invalid(
		String code,
		String field,
		String message
	) {
		return new InvalidReadingRequestException(code, field, message);
	}
}
