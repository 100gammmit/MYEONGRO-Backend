package com.myeongro.api.domain.reading.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import com.myeongro.api.domain.reading.entity.TarotSpreadType;

class TarotPromptCatalogTests {

	@ParameterizedTest
	@EnumSource(TarotSpreadType.class)
	void combinesCommonCardsAndExactSpreadPrompt(TarotSpreadType spread) {
		TarotPromptCatalog catalog = catalog();

		assertThat(catalog.prompt(spread)).isEqualTo(
			"common\n\ncards\n\n" + spread.value()
		);
		assertThat(catalog.version(spread))
			.contains("common-ko-v2")
			.contains("major-arcana-ko-v2")
			.contains(spreadFileVersion(spread));
	}

	private String spreadFileVersion(TarotSpreadType spread) {
		return switch (spread) {
			case DAILY_ONE_CARD -> "daily-one-card-ko-v3";
			case MIND_THREE_CARD -> "mind-three-card-ko-v3";
			case RELATIONSHIP_THREE_CARD -> "relationship-three-card-ko-v3";
			case CHOICE_FIVE_CARD -> "choice-five-card-ko-v3";
		};
	}

	private TarotPromptCatalog catalog() {
		return new TarotPromptCatalog(
			resource("common", "common-ko-v2.md"),
			resource("cards", "major-arcana-ko-v2.md"),
			resource("daily_one_card", "daily-one-card-ko-v3.md"),
			resource("mind_three_card", "mind-three-card-ko-v3.md"),
			resource("relationship_three_card", "relationship-three-card-ko-v3.md"),
			resource("choice_five_card", "choice-five-card-ko-v3.md")
		);
	}

	private Resource resource(String content, String filename) {
		return new ByteArrayResource(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
			@Override
			public String getFilename() {
				return filename;
			}
		};
	}
}
