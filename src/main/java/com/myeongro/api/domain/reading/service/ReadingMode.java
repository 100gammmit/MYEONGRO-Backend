package com.myeongro.api.domain.reading.service;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ReadingMode {

	STANDARD("standard"),
	HEALTH_FORTUNE("health_fortune"),
	MONEY_FORTUNE("money_fortune"),
	RELATIONSHIP_FORTUNE("relationship_fortune"),
	CAREER_LIFE_FORTUNE("career_life_fortune");

	private final String value;

	ReadingMode(String value) {
		this.value = value;
	}

	@JsonValue
	public String value() {
		return value;
	}

	@JsonCreator
	public static ReadingMode fromValue(String value) {
		return Arrays.stream(values())
			.filter(mode -> mode.value.equals(value))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Unsupported reading mode"));
	}
}
