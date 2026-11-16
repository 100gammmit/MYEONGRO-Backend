package com.myeongro.api.domain.saju.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import com.myeongro.api.domain.reading.service.ReadingInputSupport;

/** Shared validation only; does not broaden the existing AI request DTO. */
public final class SajuBirthInputRules {
	private static final int MINIMUM_BIRTH_YEAR = 1900;
	private static final int MAXIMUM_BIRTH_YEAR = 2099;
	private SajuBirthInputRules() { }
	public static LocalDate parseBirthDate(String value) {
		try {
			LocalDate date = LocalDate.parse(value);
			if (date.getYear() < MINIMUM_BIRTH_YEAR || date.getYear() > MAXIMUM_BIRTH_YEAR) {
				throw ReadingInputSupport.invalid(
					"UNSUPPORTED_BIRTH_YEAR",
					"birthProfile.birthDate",
					"출생 연도는 1900년부터 2099년까지 입력할 수 있습니다."
				);
			}
			return date;
		} catch (DateTimeParseException | NullPointerException exception) {
			throw ReadingInputSupport.invalid(
				"INVALID_BIRTH_DATE", "birthProfile.birthDate", "생년월일을 확인해 주세요."
			);
		}
	}

	public static String normalizeBirthTime(String value, BirthTimePrecision precision) {
		boolean missing = value == null || value.isBlank();
		if (precision == BirthTimePrecision.UNKNOWN) {
			if (!missing) {
				throw ReadingInputSupport.invalid(
					"INVALID_BIRTH_TIME",
					"birthProfile.birthTime",
					"출생시간을 모르는 경우 시각을 비워 주세요."
				);
			}
			return null;
		}
		if (missing || !value.matches("\\d{2}:\\d{2}")) {
			throw ReadingInputSupport.invalid(
				"INVALID_BIRTH_TIME",
				"birthProfile.birthTime",
				"출생시간을 시와 분으로 입력해 주세요."
			);
		}
		try {
			return LocalTime.parse(value).toString();
		} catch (DateTimeParseException exception) {
			throw ReadingInputSupport.invalid(
				"INVALID_BIRTH_TIME", "birthProfile.birthTime", "출생시간을 확인해 주세요."
			);
		}
	}
}
