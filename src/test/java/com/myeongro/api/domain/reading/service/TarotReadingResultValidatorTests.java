package com.myeongro.api.domain.reading.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.myeongro.api.domain.reading.dto.ReadingResult;
import com.myeongro.api.domain.reading.dto.ReadingSection;

class TarotReadingResultValidatorTests {

	private final TarotReadingResultValidator validator = new TarotReadingResultValidator();

	@Test
	void acceptsCompleteThreeCardReading() {
		assertThatCode(() -> validator.validate(validResult()))
			.doesNotThrowAnyException();
	}

	@Test
	void rejectsReadingWithoutExactlyThreeSections() {
		ReadingResult result = new ReadingResult(
			"제목",
			"요약",
			validResult().sections().subList(0, 2),
			List.of("첫 번째 제안", "두 번째 제안"),
			"오락과 자기 성찰을 위한 리딩입니다."
		);

		assertThatThrownBy(() -> validator.validate(result))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Tarot reading must contain exactly three sections");
	}

	@Test
	void rejectsReadingWithWrongSectionHeadingOrder() {
		ReadingResult result = new ReadingResult(
			"제목",
			"요약",
			List.of(
				new ReadingSection("현재 - 마법사", "현재 본문"),
				new ReadingSection("과거 - 바보", "과거 본문"),
				new ReadingSection("조언 - 힘", "조언 본문")
			),
			List.of("첫 번째 제안", "두 번째 제안"),
			"오락과 자기 성찰을 위한 리딩입니다."
		);

		assertThatThrownBy(() -> validator.validate(result))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Tarot section heading order is invalid");
	}

	@Test
	void rejectsBlankRequiredText() {
		ReadingResult result = new ReadingResult(
			" ",
			"요약",
			validResult().sections(),
			List.of("첫 번째 제안", "두 번째 제안"),
			"오락과 자기 성찰을 위한 리딩입니다."
		);

		assertThatThrownBy(() -> validator.validate(result))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Tarot reading title is required");
	}

	@Test
	void rejectsGuidanceOutsideTwoToThreeItems() {
		ReadingResult result = new ReadingResult(
			"제목",
			"요약",
			validResult().sections(),
			List.of("한 개뿐인 제안"),
			"오락과 자기 성찰을 위한 리딩입니다."
		);

		assertThatThrownBy(() -> validator.validate(result))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Tarot reading guidance must contain two or three items");
	}

	@Test
	void rejectsBlankSectionBodyAndGuidanceItem() {
		ReadingResult blankBody = new ReadingResult(
			"제목",
			"요약",
			List.of(
				new ReadingSection("과거 - 바보", " "),
				new ReadingSection("현재 - 마법사", "현재 본문"),
				new ReadingSection("조언 - 힘", "조언 본문")
			),
			List.of("첫 번째 제안", "두 번째 제안"),
			"오락과 자기 성찰을 위한 리딩입니다."
		);
		ReadingResult blankGuidance = new ReadingResult(
			"제목",
			"요약",
			validResult().sections(),
			List.of("첫 번째 제안", " "),
			"오락과 자기 성찰을 위한 리딩입니다."
		);

		assertThatThrownBy(() -> validator.validate(blankBody))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Tarot section text is required");
		assertThatThrownBy(() -> validator.validate(blankGuidance))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Tarot guidance text is required");
	}

	private ReadingResult validResult() {
		return new ReadingResult(
			"새로운 흐름을 다루는 방법",
			"지금까지의 흐름을 살피고 작은 행동으로 옮겨 보세요.",
			List.of(
				new ReadingSection("과거 - 바보", "새로운 가능성이 출발점이 되었습니다."),
				new ReadingSection("현재 - 마법사", "가진 자원을 활용할 시점입니다."),
				new ReadingSection("조언 - 힘", "서두르지 말고 꾸준히 움직여 보세요.")
			),
			List.of("오늘 할 작은 행동을 정해 보세요.", "사용 가능한 자원을 적어 보세요."),
			"이 리딩은 오락과 자기 성찰을 위한 참고입니다."
		);
	}
}
