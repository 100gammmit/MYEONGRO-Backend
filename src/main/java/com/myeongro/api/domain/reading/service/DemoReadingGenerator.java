package com.myeongro.api.domain.reading.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.myeongro.api.domain.reading.dto.GeneratedReading;
import com.myeongro.api.domain.reading.entity.ReadingKind;

@Component
public class DemoReadingGenerator implements ReadingGenerator {

	@Override
	public GeneratedReading generate(
		ReadingKind kind,
		String spreadType,
		String question,
		Map<String, Object> input
	) {
		return kind == ReadingKind.TAROT ? tarot(question) : saju(question);
	}

	private GeneratedReading tarot(String question) {
		return new GeneratedReading("타로 데모 리딩", Map.of(
			"title", "타로 데모 리딩",
			"summary", "선택한 카드가 현재 흐름을 보여줘요.",
			"sections", List.of(
				Map.of(
					"heading", "현재의 흐름",
					"body", "질문과 카드를 바탕으로 흐름을 살펴봐요: " + question
				),
				Map.of(
					"heading", "다음 선택",
					"body", "오늘 바로 시도할 수 있는 작은 행동에 집중해봐도 좋아요."
				)
			),
			"guidance", List.of("부담이 적은 행동 하나를 먼저 정해봐도 좋아요."),
			"disclaimer", "이 리딩은 오락과 자기성찰을 위한 참고 자료입니다."
		));
	}

	private GeneratedReading saju(String question) {
		return new GeneratedReading("사주 데모 리딩", Map.of(
			"title", "사주 데모 리딩",
			"summary", "입력한 생년월일을 바탕으로 현재 흐름을 간단히 정리해요.",
			"sections", List.of(Map.of(
				"heading", "오늘의 균형",
				"body", "질문을 기준으로 무리하지 않는 선택과 회복의 리듬을 살펴봐요: " + question
			)),
			"guidance", List.of("부담이 적은 선택부터 살펴봐도 괜찮아요."),
			"disclaimer", "이 리딩은 오락과 자기성찰을 위한 참고 자료입니다."
		));
	}
}
