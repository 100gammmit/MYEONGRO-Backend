package com.myeongro.api.domain.saju.place;

public record SajuBirthPlace(
	String provinceCode,
	String provinceName,
	double latitude,
	double longitude
) {
}
