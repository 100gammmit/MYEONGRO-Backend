package com.myeongro.api.domain.saju.model;

import java.util.Arrays;

import com.myeongro.api.domain.reading.exception.InvalidReadingRequestException;

public enum BirthTimePrecision {
	EXACT("exact"),
	APPROXIMATE("approximate"),
	UNKNOWN("unknown");

	private final String value;

	BirthTimePrecision(String value) {
		this.value = value;
	}

	public String value() {
		return value;
	}

	public static BirthTimePrecision fromValue(String value) {
		return Arrays.stream(values())
			.filter(candidate -> candidate.value.equals(value))
			.findFirst()
			.orElseThrow(() -> new InvalidReadingRequestException(
				"INVALID_BIRTH_TIME_PRECISION",
				"birthProfile.birthTimePrecision",
				"출생시간 정확도를 다시 선택해 주세요."
			));
	}
}
