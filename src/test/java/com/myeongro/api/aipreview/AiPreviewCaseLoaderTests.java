package com.myeongro.api.aipreview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.entity.TarotSpreadType;

class AiPreviewCaseLoaderTests {

	private final AiPreviewCaseLoader loader = new AiPreviewCaseLoader(new ObjectMapper());

	@Test
	void loadsTarotAndSajuFixtures() {
		AiPreviewCase tarot = loader.load("tarot", "mind-basic");
		AiPreviewCase saju = loader.load("saju", "career-basic");

		assertThat(tarot.kind()).isEqualTo(ReadingKind.TAROT);
		assertThat(tarot.spreadType()).isEqualTo(TarotSpreadType.MIND_THREE_CARD);
		assertThat(tarot.input()).containsKey("cards");
		assertThat(new TarotPreviewInputValidator().validate(
			tarot.spreadType(), tarot.input()
		)).containsExactly(
			"major-02-high-priestess", "major-00-fool", "major-01-magician"
		);
		assertThat(saju.kind()).isEqualTo(ReadingKind.SAJU);
		assertThat(saju.spreadType()).isNull();
		assertThat(saju.input()).containsKeys("targetYear", "calculationSnapshot");
	}

	@Test
	void loadsAllSampleFixturesWithCanonicalTarotPositions() {
		assertTarotSample(
			"sample-daily-one-card",
			TarotSpreadType.DAILY_ONE_CARD,
			List.of("today")
		);
		assertTarotSample(
			"sample-mind-three-card",
			TarotSpreadType.MIND_THREE_CARD,
			List.of("emotion", "underlying_need", "self_action")
		);
		assertTarotSample(
			"sample-relationship-three-card",
			TarotSpreadType.RELATIONSHIP_THREE_CARD,
			List.of("my_heart", "relationship_flow", "check_point")
		);
		AiPreviewCase choice = assertTarotSample(
			"sample-choice-five-card",
			TarotSpreadType.CHOICE_FIVE_CARD,
			List.of("desire", "fear", "core_value", "option_a", "option_b")
		);
		assertThat(choice.input()).containsKey("choiceOptions");

		AiPreviewCase saju = loader.load("saju", "sample");
		assertThat(saju.kind()).isEqualTo(ReadingKind.SAJU);
		assertThat(saju.spreadType()).isNull();
		assertThat(saju.input()).containsKeys(
			"focusArea", "targetYear", "calculationSnapshot"
		);
	}

	@Test
	void rejectsUnsafeCasePath() {
		assertThatThrownBy(() -> loader.load("tarot", "../secret"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("lowercase letters");
	}

	private AiPreviewCase assertTarotSample(
		String caseId,
		TarotSpreadType expectedSpread,
		List<String> expectedPositions
	) {
		AiPreviewCase previewCase = loader.load("tarot", caseId);
		assertThat(previewCase.spreadType()).isEqualTo(expectedSpread);
		assertThat(new TarotPreviewInputValidator().validate(
			previewCase.spreadType(),
			previewCase.input()
		)).hasSize(expectedPositions.size());
		List<?> cards = (List<?>)previewCase.input().get("cards");
		List<String> positions = cards.stream()
			.map(card -> (String)((Map<?, ?>)card).get("position"))
			.toList();
		assertThat(positions).containsExactlyElementsOf(expectedPositions);
		return previewCase;
	}
}
