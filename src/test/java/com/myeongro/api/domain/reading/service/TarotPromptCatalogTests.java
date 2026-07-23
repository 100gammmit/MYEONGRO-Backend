package com.myeongro.api.domain.reading.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import com.myeongro.api.domain.reading.entity.MajorArcana;
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
			.contains("common-ko-v1")
			.contains("major-arcana-ko-v1")
			.contains(spreadFileVersion(spread));
	}

	@ParameterizedTest
	@EnumSource(TarotSpreadType.class)
	void activePromptUsesKoreanInstructionsAndPreservesContractIdentifiers(
		TarotSpreadType spread
	) {
		String prompt = activeCatalog().prompt(spread);

		assertThat(prompt)
			.contains("명로의 한국어 타로 리딩 생성기")
			.contains("신뢰할 수 없는 사용자 입력")
			.contains("유효한 JSON만 반환")
			.contains(spread.value())
			.contains("untrustedUserInput")
			.contains("readingInput")
			.doesNotContain("Tone and quality:")
			.doesNotContain("Safety:")
			.doesNotContain("Output:")
			.doesNotContain("Interpret each position with a distinct purpose:");

		spread.positions().forEach(position ->
			assertThat(prompt).contains(position.id())
		);
		MajorArcana.all().forEach(cardId ->
			assertThat(prompt).contains(cardId)
		);
	}

	private String spreadFileVersion(TarotSpreadType spread) {
		return switch (spread) {
			case DAILY_ONE_CARD -> "daily-one-card-ko-v2";
			case MIND_THREE_CARD -> "mind-three-card-ko-v2";
			case RELATIONSHIP_THREE_CARD -> "relationship-three-card-ko-v2";
			case CHOICE_FIVE_CARD -> "choice-five-card-ko-v2";
		};
	}

	private TarotPromptCatalog catalog() {
		return new TarotPromptCatalog(
			resource("common", "common-ko-v1.md"),
			resource("cards", "major-arcana-ko-v1.md"),
			resource("daily_one_card", "daily-one-card-ko-v2.md"),
			resource("mind_three_card", "mind-three-card-ko-v2.md"),
			resource("relationship_three_card", "relationship-three-card-ko-v2.md"),
			resource("choice_five_card", "choice-five-card-ko-v2.md")
		);
	}

	private TarotPromptCatalog activeCatalog() {
		return new TarotPromptCatalog(
			new ClassPathResource("prompts/tarot/common-ko-v1.md"),
			new ClassPathResource("prompts/tarot/major-arcana-ko-v1.md"),
			new ClassPathResource("prompts/tarot/spreads/daily-one-card-ko-v2.md"),
			new ClassPathResource("prompts/tarot/spreads/mind-three-card-ko-v2.md"),
			new ClassPathResource("prompts/tarot/spreads/relationship-three-card-ko-v2.md"),
			new ClassPathResource("prompts/tarot/spreads/choice-five-card-ko-v2.md")
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
