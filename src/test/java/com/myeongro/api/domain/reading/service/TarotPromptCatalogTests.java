package com.myeongro.api.domain.reading.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import com.myeongro.api.domain.reading.entity.MajorArcana;
import com.myeongro.api.domain.reading.entity.TarotSpreadType;

class TarotPromptCatalogTests {

	@Test
	void selectsMultilineCardSectionsInRequestedOrder() {
		TarotPromptCatalog catalog = catalog(cardsPrompt());

		String prompt = catalog.prompt(
			TarotSpreadType.DAILY_ONE_CARD,
			List.of("major-02-high-priestess", "major-00-fool")
		);

		assertThat(prompt)
			.contains("카드 해석 시작")
			.contains("major-02-high-priestess")
			.contains("여사제의 두 번째 문단")
			.contains("major-00-fool")
			.contains("바보의 두 번째 문단")
			.contains("카드 공통 안내")
			.doesNotContain("major-01-magician");
		assertThat(prompt.indexOf("major-02-high-priestess"))
			.isLessThan(prompt.indexOf("major-00-fool"));
	}

	@Test
	void rejectsMissingCardSection() {
		String missingWorld = cardsPrompt(MajorArcana.all().subList(0, 21));

		assertThatThrownBy(() -> catalog(missingWorld))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("All major arcana prompt entries are required");
	}

	@Test
	void rejectsDuplicateAndUnsupportedCardSections() {
		String duplicate = cardsPrompt().replace(
			"## major-21-world / 테스트 카드",
			"## major-20-judgement / 테스트 카드"
		);
		String unsupported = cardsPrompt().replace(
			"## major-21-world / 테스트 카드",
			"## major-22-unknown / 테스트 카드"
		);

		assertThatThrownBy(() -> catalog(duplicate))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Tarot card prompt entry is invalid");
		assertThatThrownBy(() -> catalog(unsupported))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Tarot card prompt entry is invalid");
	}

	@Test
	void rejectsEmptyCardSection() {
		String emptyWorld = cardsPrompt().replace(
			cardSection("major-21-world"),
			"## major-21-world / 테스트 카드"
		);

		assertThatThrownBy(() -> catalog(emptyWorld))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Tarot card prompt entry is invalid");
	}

	@Test
	void derivesVersionFromConfiguredFilenames() {
		assertThat(catalog(cardsPrompt()).version(TarotSpreadType.DAILY_ONE_CARD))
			.isEqualTo("common-test+cards-test+daily-test");
	}

	private TarotPromptCatalog catalog(String cards) {
		return new TarotPromptCatalog(
			resource("common-test.md", "공통 지시"),
			resource("cards-test.md", cards),
			resource("daily-test.md", "daily spread"),
			resource("mind-test.md", "mind spread"),
			resource("relationship-test.md", "relationship spread"),
			resource("choice-test.md", "choice spread")
		);
	}

	private String cardsPrompt() {
		return cardsPrompt(MajorArcana.all());
	}

	private String cardsPrompt(List<String> cardIds) {
		String sections = cardIds.stream()
			.map(this::cardSection)
			.collect(Collectors.joining("\n\n"));
		return """
			# 카드 해석 재료

			카드 해석 시작

			%s

			## 카드 공통 안내

			카드 공통 안내
			""".formatted(sections);
	}

	private String cardSection(String cardId) {
		return """
			## %s / 테스트 카드

			첫 번째 문단

			%s
			""".formatted(cardId, detail(cardId)).strip();
	}

	private String detail(String cardId) {
		return switch (cardId) {
			case "major-00-fool" -> "바보의 두 번째 문단";
			case "major-02-high-priestess" -> "여사제의 두 번째 문단";
			default -> "선택되지 않은 카드의 두 번째 문단";
		};
	}

	private Resource resource(String filename, String content) {
		return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
			@Override
			public String getFilename() {
				return filename;
			}
		};
	}
}
