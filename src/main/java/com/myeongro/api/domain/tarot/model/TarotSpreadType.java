package com.myeongro.api.domain.tarot.model;

import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TarotSpreadType {

	MIND_THREE_CARD(
		"mind_three_card",
		List.of(
			new TarotPosition("emotion", "지금의 상황"),
			new TarotPosition("underlying_need", "영향을 주는 요인"),
			new TarotPosition("self_action", "앞으로의 전개")
		),
		1,
		1,
		3000
	),
	RELATIONSHIP_THREE_CARD(
		"relationship_three_card",
		List.of(
			new TarotPosition("my_heart", "내가 바라는 관계"),
			new TarotPosition("relationship_flow", "현재의 관계 흐름"),
			new TarotPosition("check_point", "앞으로의 관계")
		),
		1,
		1,
		2400
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
		3000
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
