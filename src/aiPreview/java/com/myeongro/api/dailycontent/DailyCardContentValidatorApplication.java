package com.myeongro.api.dailycontent;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.tarot.model.MajorArcana;

public final class DailyCardContentValidatorApplication {

	static final String DISCLAIMER =
		"이 내용은 오락과 자기 성찰을 위한 것으로 전문적인 조언을 대신하지 않아요.";
	private static final int VARIANT_COUNT = 6;
	private static final Pattern COMMAND_LANGUAGE = Pattern.compile(
		"하세요|마세요|보세요|하십시오|반드시|해야 합니다|해야 해요"
	);
	private static final Pattern FORMAL_LANGUAGE = Pattern.compile(
		"습니다|합니다|됩니다|입니다"
	);
	private static final Pattern ACTION_HEADING = Pattern.compile(
		"해봐요|나눠봐요|살펴봐요"
	);
	private static final Pattern ACTION_GUIDANCE = Pattern.compile(
		"해보|살펴보|정리해|확인해|기록해|준비해|시작해|중단해|멈춰"
	);
	private static final Pattern BANNED_GUIDANCE = Pattern.compile(
		"사용자|삶 전체|가능성 전체|미래 전체|이상한 일이 아니에요|둘은 함께 존재해요"
	);
	private static final Map<String, String> CARD_NAMES = cardNames();

	private DailyCardContentValidatorApplication() {
	}

	public static void main(String[] args) throws Exception {
		Path inputFile = Path.of(requiredProperty("daily-content.input-file"));
		ValidationMode mode = ValidationMode.parse(
			System.getProperty("daily-content.mode", "pilot")
		);
		List<String> requestedCards = parseCards(
			System.getProperty("daily-content.cards", "")
		);
		ObjectMapper objectMapper = new ObjectMapper();
		List<Content> content = objectMapper.readValue(
			inputFile.toFile(), new TypeReference<>() {
			}
		);
		ValidationReport report = validate(content, mode, requestedCards);
		System.out.println(objectMapper.writerWithDefaultPrettyPrinter()
			.writeValueAsString(report));
	}

	static ValidationReport validate(
		List<Content> content,
		ValidationMode mode,
		List<String> requestedCards
	) {
		if (content == null || content.stream().anyMatch(item ->
			item == null || item.cardId() == null || !MajorArcana.contains(item.cardId()))) {
			throw new IllegalArgumentException("Daily-card content contains an unsupported card");
		}
		List<String> expectedCards = expectedCards(mode, requestedCards);
		if (content.size() != expectedCards.size() * VARIANT_COUNT) {
			throw new IllegalArgumentException("Daily-card content entry count is invalid");
		}
		Set<String> expectedCardSet = Set.copyOf(expectedCards);
		Set<String> actualCards = content.stream().map(Content::cardId)
			.collect(Collectors.toSet());
		if (!actualCards.equals(expectedCardSet)) {
			throw new IllegalArgumentException("Daily-card content card set is invalid");
		}

		Set<String> uniqueContent = new HashSet<>();
		for (String cardId : expectedCards) {
			validateCard(cardId, content.stream()
				.filter(item -> item.cardId().equals(cardId))
				.toList(), uniqueContent);
		}
		return new ValidationReport(
			mode.name().toLowerCase(Locale.ROOT), expectedCards.size(), content.size(),
			List.copyOf(expectedCards), true
		);
	}

	private static List<String> expectedCards(
		ValidationMode mode,
		List<String> requestedCards
	) {
		if (mode == ValidationMode.RELEASE) {
			if (!requestedCards.isEmpty()) {
				throw new IllegalArgumentException("Release validation does not accept a card subset");
			}
			return MajorArcana.all();
		}
		if (requestedCards.isEmpty() || requestedCards.size() > 10) {
			throw new IllegalArgumentException(
				"Pilot validation requires between 1 and 10 cards"
			);
		}
		return requestedCards;
	}

	private static void validateCard(
		String cardId,
		List<Content> variants,
		Set<String> uniqueContent
	) {
		Set<Integer> indexes = variants.stream().map(Content::variantIndex)
			.collect(Collectors.toSet());
		if (variants.size() != VARIANT_COUNT
			|| !indexes.equals(Set.of(0, 1, 2, 3, 4, 5))) {
			throw new IllegalArgumentException("Invalid variants for " + cardId);
		}
		String cardName = cardName(cardId);
		for (Content item : variants) {
			String location = item.cardId() + "#" + item.variantIndex();
			validateText(item.cardId(), "cardId");
			validateText(item.title(), "title");
			if (item.today() == null) {
				throw new IllegalArgumentException("Missing daily-card field: today");
			}
			validateText(item.today().heading(), "today.heading");
			validateText(item.today().body(), "today.body");
			if (item.guidance() == null || item.guidance().size() != 1) {
				throw invalid(location, "Guidance must contain exactly one message");
			}
			String guidance = item.guidance().getFirst();
			validateText(guidance, "guidance");
			if (!DISCLAIMER.equals(item.disclaimer())) {
				throw invalid(location, "Daily-card disclaimer is not canonical");
			}
			if (occurrences(item.today().body(), cardName) != 1) {
				throw invalid(location, "Card name evidence is not exactly once");
			}
			String userText = String.join(" ", item.title(), item.today().heading(),
				item.today().body(), guidance);
			if (COMMAND_LANGUAGE.matcher(userText).find()) {
				throw invalid(location, "Command language is not allowed");
			}
			if (FORMAL_LANGUAGE.matcher(userText).find()) {
				throw invalid(location, "Formal language is not allowed");
			}
			if (ACTION_HEADING.matcher(item.today().heading()).find()) {
				throw invalid(location, "Action language is not allowed in heading");
			}
			if (!guidance.matches("[^.!?]+\\.")) {
				throw invalid(location, "Guidance must be exactly one declarative sentence");
			}
			if (ACTION_GUIDANCE.matcher(guidance).find()) {
				throw invalid(location, "Action language is not allowed in guidance");
			}
			if (BANNED_GUIDANCE.matcher(guidance).find()) {
				throw invalid(location, "Generic counseling language is not allowed");
			}
			String fingerprint = String.join("|", item.title(), item.today().heading(),
				item.today().body(), guidance);
			if (!uniqueContent.add(fingerprint)) {
				throw invalid(location, "Duplicate daily-card content is not allowed");
			}
		}
	}

	private static IllegalArgumentException invalid(String location, String message) {
		return new IllegalArgumentException(location + ": " + message);
	}

	private static void validateText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Missing daily-card field: " + field);
		}
	}

	private static int occurrences(String text, String target) {
		Pattern cardNameMention = Pattern.compile(
			"(?<![가-힣])" + Pattern.quote(target)
				+ "(?=(?:은|는|이|가|의|을|를|과|와|에서|에게|으로|로|처럼)?(?:\\s|[,.!?:;]|$))"
		);
		return Math.toIntExact(cardNameMention.matcher(text).results().count());
	}

	static String cardName(String cardId) {
		return CARD_NAMES.get(cardId);
	}

	private static String requiredProperty(String name) {
		String value = System.getProperty(name);
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Missing system property: " + name);
		}
		return value;
	}

	private static List<String> parseCards(String value) {
		if (value == null || value.isBlank()) {
			return List.of();
		}
		List<String> cards = Pattern.compile(",").splitAsStream(value)
			.map(String::trim).filter(cardId -> !cardId.isEmpty()).distinct().toList();
		if (cards.stream().anyMatch(cardId -> !MajorArcana.contains(cardId))) {
			throw new IllegalArgumentException("Unsupported daily-card ID");
		}
		return cards;
	}

	private static Map<String, String> cardNames() {
		List<String> names = List.of(
			"바보", "마법사", "여사제", "여제", "황제", "교황", "연인", "전차", "힘",
			"은둔자", "운명의 수레바퀴", "정의", "매달린 사람", "죽음", "절제", "악마",
			"탑", "별", "달", "태양", "심판", "세계"
		);
		Map<String, String> result = new LinkedHashMap<>();
		for (int index = 0; index < MajorArcana.all().size(); index++) {
			result.put(MajorArcana.all().get(index), names.get(index));
		}
		return Map.copyOf(result);
	}

	enum ValidationMode {
		PILOT,
		RELEASE;

		static ValidationMode parse(String value) {
			try {
				return valueOf(value.strip().toUpperCase(Locale.ROOT));
			} catch (RuntimeException exception) {
				throw new IllegalArgumentException("Unsupported daily-card validation mode");
			}
		}
	}

	record Today(String heading, String body) {
	}

	record Content(
		String cardId,
		int variantIndex,
		String title,
		Today today,
		List<String> guidance,
		String disclaimer
	) {
	}

	record ValidationReport(
		String mode,
		int cardCount,
		int entryCount,
		List<String> cards,
		boolean valid
	) {
	}
}
