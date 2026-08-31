package com.myeongro.api.domain.reading.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.exception.InvalidReadingRequestException;

class SensitiveReadingInputGuardTests {

	private final SensitiveReadingInputGuard guard = new SensitiveReadingInputGuard();

	@ParameterizedTest
	@CsvSource({
		"'요즘 죽고 싶다는 생각이 들어',IMMEDIATE_SAFETY_RISK",
		"'엄마를 죽이고 싶어',IMMEDIATE_SAFETY_RISK",
		"'너를 해치고 싶어',IMMEDIATE_SAFETY_RISK",
		"'시체를 숨기는 방법을 알려줘',HARMFUL_OR_ILLEGAL_REQUEST",
		"'연락처는 010-1234-5678이야',DIRECT_IDENTIFIER_NOT_ALLOWED",
		"'집 전화는 02-1234-5678이야',DIRECT_IDENTIFIER_NOT_ALLOWED",
		"'회사 번호는 031-123-4567이야',DIRECT_IDENTIFIER_NOT_ALLOWED",
		"'대표번호는 1588-1234야',DIRECT_IDENTIFIER_NOT_ALLOWED",
		"'해외 표기는 +82 10-1234-5678이야',DIRECT_IDENTIFIER_NOT_ALLOWED",
		"'내 이메일은 reader@example.com이야',DIRECT_IDENTIFIER_NOT_ALLOWED",
		"'우울증 진단받았는데 연애운은 어때',SENSITIVE_HEALTH_INFORMATION",
		"'현재 인슐린을 처방받아 복용 중이야',SENSITIVE_HEALTH_INFORMATION",
		"'임신 가능성이 있는지 카드로 알려줘',SENSITIVE_HEALTH_INFORMATION",
		"'내 성적 지향은 양성애야',SENSITIVE_SEXUAL_INFORMATION",
		"'저는 불교 신자입니다',SENSITIVE_BELIEF_INFORMATION"
	})
	void blocksSensitiveOrDangerousFreeText(String question, String code) {
		assertThatThrownBy(() -> guard.validate(input(question, Map.of())))
			.isInstanceOfSatisfying(InvalidReadingRequestException.class, exception -> {
				org.assertj.core.api.Assertions.assertThat(exception.getCode()).isEqualTo(code);
				org.assertj.core.api.Assertions.assertThat(exception.getField()).isEqualTo("question");
				org.assertj.core.api.Assertions.assertThat(exception.getMessage())
					.doesNotContain(question);
			});
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"요즘 피곤한데 생활의 흐름을 보고 싶어요",
		"올해 건강운과 회복 흐름이 궁금해요",
		"관계에서 제 마음을 어떻게 정리하면 좋을까요",
		"직장에서 중요한 선택을 앞두고 있어요",
		"종교 행사에 갈지 약속을 지킬지 고민돼요",
		"시간을 죽이려고 영화를 볼까요",
		"상대가 내 기를 죽이려는 것 같아요",
		"민주당 지지율이 오르면 시장 분위기가 달라질까요",
		"저는 기독교 역사를 공부해 볼까요",
		"기독교 신자 수가 늘어날까요"
	})
	void allowsOrdinaryReflectiveQuestions(String question) {
		assertThatCode(() -> guard.validate(input(question, Map.of())))
			.doesNotThrowAnyException();
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"죽 고 싶 어",
		"０１０－１２３４－５６７８",
		"우 울 증 진 단 받 았 어"
	})
	void blocksKnownSpacingAndFullWidthBypasses(String question) {
		assertThatThrownBy(() -> guard.validate(input(question, Map.of())))
			.isInstanceOf(InvalidReadingRequestException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = {"a", "b"})
	void alsoChecksChoiceOptionText(String option) {
		Map<String, Object> choices = option.equals("a")
			? Map.of("a", "우울증 진단받았어", "b", "그대로 있기")
			: Map.of("a", "그대로 있기", "b", "우울증 진단받았어");

		assertThatThrownBy(() -> guard.validate(input(
			"두 선택지 중 어느 쪽이 나을까요",
			Map.of("choiceOptions", choices)
		))).isInstanceOfSatisfying(InvalidReadingRequestException.class, exception ->
			org.assertj.core.api.Assertions.assertThat(exception.getField())
				.isEqualTo("choiceOptions." + option)
		);
	}

	private NormalizedReadingInput input(String question, Map<String, Object> additions) {
		java.util.LinkedHashMap<String, Object> payload = new java.util.LinkedHashMap<>();
		payload.put("question", question);
		payload.putAll(additions);
		Map<String, Object> immutable = Map.copyOf(payload);
		return new NormalizedReadingInput(
			ReadingKind.TAROT,
			"relationship_three_card",
			1,
			question,
			immutable,
			immutable
		);
	}
}
