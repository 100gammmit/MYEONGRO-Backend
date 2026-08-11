package com.myeongro.api.domain.reading.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;

import com.myeongro.api.domain.reading.dto.ReadingResult;
import com.myeongro.api.domain.reading.dto.ReadingSection;
import com.myeongro.api.domain.reading.entity.TarotSpreadType;

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
		ReadingResult valid = result(TarotSpreadType.MIND_THREE_CARD);
		ReadingResult wrong = new ReadingResult(
			valid.title(),
			valid.summary(),
			List.of(valid.sections().get(1), valid.sections().get(0), valid.sections().get(2)),
			valid.guidance(),
			valid.disclaimer()
		);

		assertThatThrownBy(() -> validator.validate(TarotSpreadType.MIND_THREE_CARD, wrong))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void allSpreadsAllowOneGuidanceAndRejectMoreThanTwo() {
		ReadingResult daily = result(TarotSpreadType.DAILY_ONE_CARD);
		assertThatCode(() -> validator.validate(TarotSpreadType.DAILY_ONE_CARD, daily))
			.doesNotThrowAnyException();

		ReadingResult mind = result(TarotSpreadType.MIND_THREE_CARD);
		ReadingResult one = new ReadingResult(
			mind.title(), mind.summary(), mind.sections(), List.of("하나"), mind.disclaimer()
		);
		assertThatCode(() -> validator.validate(TarotSpreadType.MIND_THREE_CARD, one))
			.doesNotThrowAnyException();
		ReadingResult invalid = new ReadingResult(
			mind.title(), mind.summary(), mind.sections(), List.of("하나", "둘", "셋"), mind.disclaimer()
		);
		assertThatThrownBy(() -> validator.validate(TarotSpreadType.MIND_THREE_CARD, invalid))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private ReadingResult result(TarotSpreadType spread) {
		return new ReadingResult(
			"제목",
			"요약",
			spread.positions().stream()
				.map(position -> new ReadingSection(position.id(), position.displayName(), "본문"))
				.toList(),
			spread == TarotSpreadType.DAILY_ONE_CARD
				? List.of("작은 행동")
				: List.of("행동 하나", "행동 둘"),
			"오락과 자기 성찰을 위한 참고입니다."
		);
	}
}
