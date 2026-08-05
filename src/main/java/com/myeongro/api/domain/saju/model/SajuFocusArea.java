package com.myeongro.api.domain.saju.model;

import java.util.Arrays;

import com.myeongro.api.domain.reading.exception.InvalidReadingRequestException;

public enum SajuFocusArea {
	SELF("self"),
	CAREER("career"),
	RELATIONSHIP("relationship"),
	LIFE_MONEY("life_money");

	private final String value;

	SajuFocusArea(String value) {
		this.value = value;
	}

	public String value() {
		return value;
	}

	public static SajuFocusArea fromValue(String value) {
		return Arrays.stream(values())
			.filter(candidate -> candidate.value.equals(value))
			.findFirst()
			.orElseThrow(() -> new InvalidReadingRequestException(
				"INVALID_FOCUS_AREA",
				"focusArea",
				"관심 분야를 다시 선택해 주세요."
			));
	}
}
