package com.myeongro.api.domain.tarot.model;

import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TarotSpreadType {

	DAILY_ONE_CARD(
		"daily_one_card",
		List.of(new TarotPosition("today", "오늘의 흐름")),
		1,
		1,
		800
	),
	MIND_THREE_CARD(
		"mind_three_card",
		List.of(
			new TarotPosition("emotion", "지금의 감정"),
			new TarotPosition("underlying_need", "감정 뒤의 욕구"),
			new TarotPosition("self_action", "나를 위한 행동")
		),
		1,
		1,
		1800
	),
	RELATIONSHIP_THREE_CARD(
		"relationship_three_card",
		List.of(
			new TarotPosition("my_heart", "내가 가져온 마음"),
			new TarotPosition("relationship_flow", "관계에서 드러난 흐름"),
			new TarotPosition("check_point", "내가 확인할 것")
		),
		1,
		1,
		1800
	),
	CHOICE_FIVE_CARD(
		"choice_five_card",
		List.of(
			new TarotPosition("desire", "원하는 것"),
			new TarotPosition("fear", "두려운 것"),
			new TarotPosition("core_value", "중요한 가치"),
			new TarotPosition("option_a", "선택 A"),
			new TarotPosition("option_b", "선택 B")
		),
		1,
		1,
		2700
	);

	private final String value;
	private final List<TarotPosition> positions;
	private final int minGuidanceItems;
	private final int maxGuidanceItems;
	private final int maxOutputTokens;

	TarotSpreadType(
		String value,
		List<TarotPosition> positions,
		int minGuidanceItems,
		int maxGuidanceItems,
		int maxOutputTokens
	) {
		this.value = value;
		this.positions = List.copyOf(positions);
		this.minGuidanceItems = minGuidanceItems;
		this.maxGuidanceItems = maxGuidanceItems;
		this.maxOutputTokens = maxOutputTokens;
	}

	@JsonValue
	public String value() {
		return value;
	}

	public List<TarotPosition> positions() {
		return positions;
	}

	public int cardCount() {
		return positions.size();
	}

	public int minGuidanceItems() {
		return minGuidanceItems;
	}

	public int maxGuidanceItems() {
		return maxGuidanceItems;
	}

	public int maxOutputTokens() {
		return maxOutputTokens;
	}

	public boolean supportsAiGeneration() {
		return this != DAILY_ONE_CARD;
	}

	@JsonCreator
	public static TarotSpreadType fromValue(String value) {
		return Arrays.stream(values())
			.filter(spread -> spread.value.equals(value))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException(
				"Unsupported tarot spread type: " + value
			));
	}
}
