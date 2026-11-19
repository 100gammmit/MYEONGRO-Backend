package com.myeongro.api.domain.saju.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.myeongro.api.domain.reading.exception.InvalidReadingRequestException;
import com.myeongro.api.domain.reading.service.ReadingInputSupport;
import com.myeongro.api.domain.saju.calculation.SajuCalculationOptions;

/** Birth-input validation shared by AI readings and the public manseryeok. */
public final class SajuBirthInputRules {
	private static final int MINIMUM_BIRTH_YEAR = 1900;
	private static final int MAXIMUM_BIRTH_YEAR = 2099;
	private static final Set<String> OPTION_FIELDS = Set.of("dayBoundary", "equationOfTime");
	private SajuBirthInputRules() { }

	/**
	 * Calculation options. {@code null} (field absent) means the defaults; each inner field may also be omitted.
	 * An explicit JSON null, a non-object, a wrong type or an unknown field is rejected. {@code field} is the
	 * request field name used in error paths, e.g. {@code options} or {@code calculationOptions}.
	 */
	public static SajuCalculationOptions parseCalculationOptions(JsonNode options, String field) {
		if (options == null) return SajuCalculationOptions.DEFAULT;
		if (!options.isObject()) throw invalidOptions(field);
		options.fieldNames().forEachRemaining(key -> {
			if (!OPTION_FIELDS.contains(key)) throw InvalidReadingRequestException.unknownField(field + "." + key);
		});
		SajuCalculationOptions.DayBoundary boundary = SajuCalculationOptions.DEFAULT.dayBoundary();
		boolean equation = SajuCalculationOptions.DEFAULT.equationOfTime();
		if (options.has("dayBoundary")) {
			JsonNode value = options.get("dayBoundary");
			if (!value.isTextual() || !Set.of("MIDNIGHT", "HOUR_23").contains(value.textValue())) {
				throw invalidOptions(field + ".dayBoundary");
			}
			boundary = SajuCalculationOptions.DayBoundary.valueOf(value.textValue());
		}
		if (options.has("equationOfTime")) {
			if (!options.get("equationOfTime").isBoolean()) throw invalidOptions(field + ".equationOfTime");
			equation = options.get("equationOfTime").booleanValue();
		}
		return new SajuCalculationOptions(boundary, equation);
	}

	private static InvalidReadingRequestException invalidOptions(String field) {
		return ReadingInputSupport.invalid("INVALID_CALCULATION_OPTIONS", field, "입력한 계산 정보를 확인해 주세요.");
	}
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
