package com.myeongro.api.domain.reading.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.core.io.ClassPathResource;

import com.myeongro.api.domain.reading.entity.TarotSpreadType;

class TarotPromptCatalogTests {

	@ParameterizedTest
	@EnumSource(TarotSpreadType.class)
	void combinesCommonCardsAndExactSpreadPrompt(TarotSpreadType spread) {
		TarotPromptCatalog catalog = catalog();

		assertThat(catalog.prompt(spread))
			.contains("MYEONGRO's Korean tarot reading generator")
			.contains("Major Arcana card meanings")
			.contains("Spread: " + spread.value());
		assertThat(catalog.version(spread))
			.contains("common-ko-v1")
			.contains("major-arcana-ko-v1")
			.contains(spreadFileVersion(spread));
	}

	private String spreadFileVersion(TarotSpreadType spread) {
		return switch (spread) {
			case DAILY_ONE_CARD -> "daily-one-card-ko-v1";
			case MIND_THREE_CARD -> "mind-three-card-ko-v1";
			case RELATIONSHIP_THREE_CARD -> "relationship-three-card-ko-v1";
			case CHOICE_FIVE_CARD -> "choice-five-card-ko-v1";
		};
	}

	private TarotPromptCatalog catalog() {
		return new TarotPromptCatalog(
			new ClassPathResource("prompts/tarot/common-ko-v1.md"),
			new ClassPathResource("prompts/tarot/major-arcana-ko-v1.md"),
			new ClassPathResource("prompts/tarot/spreads/daily-one-card-ko-v1.md"),
			new ClassPathResource("prompts/tarot/spreads/mind-three-card-ko-v1.md"),
			new ClassPathResource("prompts/tarot/spreads/relationship-three-card-ko-v1.md"),
			new ClassPathResource("prompts/tarot/spreads/choice-five-card-ko-v1.md")
		);
	}
}
