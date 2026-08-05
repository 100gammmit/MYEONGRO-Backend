package com.myeongro.api.domain.saju.model;

import java.util.Arrays;

import com.myeongro.api.domain.reading.exception.InvalidReadingRequestException;

public enum LuckDirectionBasis {
	MALE("male"),
	FEMALE("female"),
	UNSPECIFIED("unspecified");

	private final String value;

	LuckDirectionBasis(String value) {
		this.value = value;
	}

	public String value() {
		return value;
	}

	public static LuckDirectionBasis fromValue(String value) {
		return Arrays.stream(values())
			.filter(candidate -> candidate.value.equals(value))
			.findFirst()
			.orElseThrow(() -> new InvalidReadingRequestException(
				"INVALID_LUCK_DIRECTION_BASIS",
				"birthProfile.luckDirectionBasis",
				"대운 계산 기준을 다시 선택해 주세요."
			));
	}
}
