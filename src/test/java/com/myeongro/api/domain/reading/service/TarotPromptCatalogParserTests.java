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

class TarotPromptCatalogParserTests {

	@Test
	void selectsSyntheticCardSectionsInRequestedOrder() {
		TarotPromptCatalog catalog = catalog(cardsPrompt(MajorArcana.all()));

		String prompt = catalog.prompt(
			TarotSpreadType.DAILY_ONE_CARD,
			List.of("major-02-high-priestess", "major-00-fool")
		);

		assertThat(prompt)
			.contains("synthetic-preamble")
			.contains("synthetic-body-major-02-high-priestess")
			.contains("synthetic-body-major-00-fool")
			.contains("synthetic-postamble")
			.doesNotContain("synthetic-body-major-01-magician");
		assertThat(prompt.indexOf("synthetic-body-major-02-high-priestess"))
			.isLessThan(prompt.indexOf("synthetic-body-major-00-fool"));
	}

	@Test
	void rejectsMissingSyntheticCardSection() {
		String missingWorld = cardsPrompt(MajorArcana.all().subList(0, 21));

		assertThatThrownBy(() -> catalog(missingWorld))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("All major arcana prompt entries are required");
	}

	@Test
	void rejectsDuplicateAndUnsupportedSyntheticCardSections() {
		String duplicate = cardsPrompt(MajorArcana.all()).replace(
			heading("major-21-world"),
			heading("major-20-judgement")
		);
		String unsupported = cardsPrompt(MajorArcana.all()).replace(
			heading("major-21-world"),
			heading("major-22-unknown")
		);

		assertThatThrownBy(() -> catalog(duplicate))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Tarot card prompt entry is invalid");
		assertThatThrownBy(() -> catalog(unsupported))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Tarot card prompt entry is invalid");
	}

	@Test
	void rejectsEmptySyntheticCardSection() {
		String emptyWorld = cardsPrompt(MajorArcana.all()).replace(
			cardSection("major-21-world"),
			heading("major-21-world")
		);

		assertThatThrownBy(() -> catalog(emptyWorld))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Tarot card prompt entry is invalid");
	}

	private TarotPromptCatalog catalog(String cards) {
		return new TarotPromptCatalog(
			resource("synthetic-common.md", "synthetic-common"),
			resource("synthetic-cards.md", cards),
			resource("synthetic-daily.md", "synthetic-daily-spread"),
			resource("synthetic-mind.md", "synthetic-mind-spread"),
			resource("synthetic-relationship.md", "synthetic-relationship-spread"),
			resource("synthetic-choice.md", "synthetic-choice-spread")
		);
	}

	private String cardsPrompt(List<String> cardIds) {
		String sections = cardIds.stream()
			.map(this::cardSection)
			.collect(Collectors.joining("\n\n"));
		return """
			synthetic-preamble

			%s

			## 카드 공통 안내

			synthetic-postamble
			""".formatted(sections);
	}

	private String cardSection(String cardId) {
		return """
			%s

			synthetic-body-%s
			synthetic-detail-%s
			""".formatted(heading(cardId), cardId, cardId).strip();
	}

	private String heading(String cardId) {
		return "## %s / synthetic-card".formatted(cardId);
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
