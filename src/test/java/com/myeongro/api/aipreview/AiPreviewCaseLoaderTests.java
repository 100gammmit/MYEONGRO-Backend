package com.myeongro.api.aipreview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
	void rejectsUnsafeCasePath() {
		assertThatThrownBy(() -> loader.load("tarot", "../secret"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("lowercase letters");
	}
}
