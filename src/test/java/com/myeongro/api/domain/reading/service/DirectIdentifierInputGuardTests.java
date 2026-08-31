package com.myeongro.api.domain.reading.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.exception.InvalidReadingRequestException;

class DirectIdentifierInputGuardTests {

	private final DirectIdentifierInputGuard guard = new DirectIdentifierInputGuard();

	@ParameterizedTest
	@ValueSource(strings = {
		"내 이메일은 reader@example.com이야",
		"연락처는 010-1234-5678이야",
		"해외 표기는 +82 10-1234-5678이야",
		"집 전화는 02-1234-5678이야",
		"서울 국제 표기는 +82 2-1234-5678이야",
		"서울 0082 표기는 0082 2-1234-5678이야",
		"지역 국제 표기는 +82 31-123-4567이야",
		"지역 0082 표기는 0082 31-123-4567이야",
		"070 국제 표기는 +82 70-1234-5678이야",
		"070 0082 표기는 0082 70-1234-5678이야",
		"대표번호는 1588-1234야",
		"주민번호는 000101-3123451이야",
		"카드는 4111 1111 1111 1111이야",
		"전각 카드는 ４１１１－１１１１－１１１１－１１１１이야"
	})
	void blocksVerifiableDirectIdentifiers(String question) {
		assertThatThrownBy(() -> guard.validate(input(question, Map.of())))
			.isInstanceOfSatisfying(InvalidReadingRequestException.class, exception -> {
				org.assertj.core.api.Assertions.assertThat(exception.getCode())
					.isEqualTo("DIRECT_IDENTIFIER_NOT_ALLOWED");
				org.assertj.core.api.Assertions.assertThat(exception.getField()).isEqualTo("question");
				org.assertj.core.api.Assertions.assertThat(exception.getMessage())
					.doesNotContain(question);
			});
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"우울증 진단을 받았는데 올해 흐름이 궁금해요",
		"저는 불교 신자입니다",
		"정당 가입을 고민하고 있어요",
		"죽고 싶다는 생각이 들어요",
		"성적 지향에 관해 고민하고 있어요",
		"유효하지 않은 카드는 4111 1111 1111 1112야",
		"체크섬이 틀린 번호는 000101-3123450이야"
	})
	void doesNotInferSensitiveMeaningOrBlockInvalidChecksums(String question) {
		assertThatCode(() -> guard.validate(input(question, Map.of())))
			.doesNotThrowAnyException();
	}

	@ParameterizedTest
	@ValueSource(strings = {"a", "b"})
	void alsoChecksChoiceOptionText(String option) {
		Map<String, Object> choices = option.equals("a")
			? Map.of("a", "reader@example.com", "b", "그대로 있기")
			: Map.of("a", "그대로 있기", "b", "reader@example.com");

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
