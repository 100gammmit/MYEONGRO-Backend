package com.myeongro.api.domain.saju.model;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.myeongro.api.domain.reading.exception.InvalidReadingRequestException;

public record SajuBirthProfileRequest(
	String calendarType,
	String birthDate,
	String birthTime,
	String birthTimePrecision,
	String provinceCode,
	String luckDirectionBasis
) {

	@JsonAnySetter
	void rejectUnknownField(String name, Object value) {
		throw InvalidReadingRequestException.unknownField("birthProfile." + name);
	}
}
