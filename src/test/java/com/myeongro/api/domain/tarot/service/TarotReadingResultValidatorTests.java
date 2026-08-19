package com.myeongro.api.domain.tarot.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;

import com.myeongro.api.domain.tarot.result.TarotReadingResult;
import com.myeongro.api.domain.tarot.result.TarotReadingSection;
import com.myeongro.api.domain.tarot.model.TarotSpreadType;

class TarotReadingResultValidatorTests {

	private final TarotReadingResultValidator validator = new TarotReadingResultValidator();

	@ParameterizedTest
	@EnumSource(TarotSpreadType.class)
	void acceptsExactSectionCountAndPositionOrder(TarotSpreadType spread) {
		assertThatCode(() -> validator.validate(spread, result(spread)))
			.doesNotThrowAnyException();
	}

	@Test
	void rejectsWrongPositionOrder() {
		TarotReadingResult valid = result(TarotSpreadType.MIND_THREE_CARD);
		TarotReadingResult wrong = new TarotReadingResult(
			valid.title(),
			valid.summary(),
			List.of(valid.sections().get(1), valid.sections().get(0), valid.sections().get(2)),
			valid.guidance(),
			valid.disclaimer()
		);

		assertThatThrownBy(() -> validator.validate(TarotSpreadType.MIND_THREE_CARD, wrong))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest
	@EnumSource(TarotSpreadType.class)
	void rejectsTwoGuidanceItems(TarotSpreadType spread) {
		TarotReadingResult valid = result(spread);
		TarotReadingResult invalid = new TarotReadingResult(
			valid.title(), valid.summary(), valid.sections(), List.of("하나", "둘"), valid.disclaimer()
		);
		assertThatThrownBy(() -> validator.validate(spread, invalid))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private TarotReadingResult result(TarotSpreadType spread) {
		return new TarotReadingResult(
			"제목",
			"요약",
			spread.positions().stream()
				.map(position -> new TarotReadingSection(position.id(), position.displayName(), "본문"))
				.toList(),
			List.of("작은 행동"),
			"오락과 자기 성찰을 위한 참고입니다."
		);
	}
}
