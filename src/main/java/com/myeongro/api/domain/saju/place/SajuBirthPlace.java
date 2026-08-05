package com.myeongro.api.domain.saju.place;

public record SajuBirthPlace(
	String provinceCode,
	String provinceName,
	String cityCode,
	String cityName,
	double latitude,
	double longitude
) {
}
