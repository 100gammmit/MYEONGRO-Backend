package com.myeongro.api.domain.saju.controller;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.myeongro.api.domain.reading.exception.InvalidReadingRequestException;
import com.myeongro.api.domain.reading.service.ReadingInputSupport;
import com.myeongro.api.domain.saju.calculation.SajuCalculationOptions;
import com.myeongro.api.domain.saju.model.*;
import com.myeongro.api.domain.saju.place.SajuBirthPlaceCatalog;
import java.util.*;
import io.swagger.v3.oas.annotations.media.Schema;

/** JsonNode leaves scalar coercion off at this public input boundary without changing global Jackson. */
public record SajuCalculationRequest(
	@Schema(requiredMode=Schema.RequiredMode.REQUIRED, description="solar만 지원. birthDate(1900~2099 YYYY-MM-DD), birthTimePrecision(exact/approximate/unknown), luckDirectionBasis(male/female/unspecified) 필수. exact/approximate는 birthTime(HH:mm), provinceCode 필수, cityCode 선택. unknown은 시각·출생지 필드 생략.",
		example="{\"calendarType\":\"solar\",\"birthDate\":\"2000-06-15\",\"birthTime\":\"14:30\",\"birthTimePrecision\":\"exact\",\"provinceCode\":\"11\",\"luckDirectionBasis\":\"male\"}") JsonNode birthProfile,
	@Schema(description="생략하면 MIDNIGHT / true. 객체 안에서도 각 필드를 생략할 수 있습니다. 명시적 null·다른 필드·타입 변환은 허용하지 않습니다. HOUR_23은 23시 일주 경계, equationOfTime=false는 균시차만 제외합니다.",
		example="{\"dayBoundary\":\"MIDNIGHT\",\"equationOfTime\":true}") JsonNode options) {
	@JsonAnySetter void reject(String name, Object ignored) { throw InvalidReadingRequestException.unknownField(name); }
	public Validated validate(SajuBirthPlaceCatalog catalog) {
		if (birthProfile == null || !birthProfile.isObject()) throw invalid("INVALID_BIRTH_DATE", "birthProfile");
		checkFields(birthProfile, Set.of("calendarType", "birthDate", "birthTime", "birthTimePrecision", "provinceCode", "cityCode", "luckDirectionBasis"), "birthProfile.");
		if (!"solar".equals(text(birthProfile, "calendarType", false))) throw invalid("UNSUPPORTED_CALENDAR_TYPE", "birthProfile.calendarType");
		String dateInput = text(birthProfile, "birthDate", false);
		if (!dateInput.matches("\\d{4}-\\d{2}-\\d{2}")) throw invalid("INVALID_BIRTH_DATE", "birthProfile.birthDate");
		String date = SajuBirthInputRules.parseBirthDate(dateInput).toString();
		BirthTimePrecision precision = BirthTimePrecision.fromValue(text(birthProfile, "birthTimePrecision", false));
		String time = SajuBirthInputRules.normalizeBirthTime(text(birthProfile, "birthTime", true), precision);
		LuckDirectionBasis basis = LuckDirectionBasis.fromValue(text(birthProfile, "luckDirectionBasis", false));
		Map<String, Object> profile = new LinkedHashMap<>();
		profile.put("calendarType", "solar"); profile.put("birthDate", date); profile.put("birthTimePrecision", precision.value()); profile.put("luckDirectionBasis", basis.value());
		double longitude = 127.5; String source = "reference";
		if (precision == BirthTimePrecision.UNKNOWN) {
			for (String key : List.of("birthTime", "provinceCode", "cityCode")) if (birthProfile.has(key)) throw invalid(key.equals("birthTime") ? "INVALID_BIRTH_TIME" : "INVALID_BIRTH_PLACE", "birthProfile." + key);
		} else {
			String province = text(birthProfile, "provinceCode", false);
			longitude = catalog.requireProvince(province).longitude(); source = "province";
			profile.put("birthTime", time); profile.put("provinceCode", province);
			if (birthProfile.has("cityCode")) { longitude = catalog.requireCity(province, text(birthProfile, "cityCode", false)).longitude(); source = "city"; }
		}
		SajuCalculationOptions.DayBoundary boundary = SajuCalculationOptions.DayBoundary.MIDNIGHT;
		boolean equation = true;
		if (options != null) {
			if (!options.isObject()) throw invalid("INVALID_CALCULATION_OPTIONS", "options");
			checkFields(options, Set.of("dayBoundary", "equationOfTime"), "options.");
			if (options.has("dayBoundary")) {
				JsonNode value = options.get("dayBoundary");
				if (!value.isTextual() || !Set.of("MIDNIGHT", "HOUR_23").contains(value.textValue())) throw invalid("INVALID_CALCULATION_OPTIONS", "options.dayBoundary");
				boundary = SajuCalculationOptions.DayBoundary.valueOf(value.textValue());
			}
			if (options.has("equationOfTime")) {
				if (!options.get("equationOfTime").isBoolean()) throw invalid("INVALID_CALCULATION_OPTIONS", "options.equationOfTime");
				equation = options.get("equationOfTime").booleanValue();
			}
		}
		return new Validated(Collections.unmodifiableMap(profile), new SajuCalculationOptions(boundary, equation), longitude, source);
	}
	private static String text(JsonNode node, String key, boolean optional) {
		if (!node.has(key) && optional) return null;
		JsonNode value = node.get(key);
		if (value == null || !value.isTextual()) throw invalid("INVALID_BIRTH_INPUT", "birthProfile." + key);
		return value.textValue();
	}
	private static void checkFields(JsonNode node, Set<String> allowed, String prefix) {
		node.fieldNames().forEachRemaining(key -> { if (!allowed.contains(key)) throw InvalidReadingRequestException.unknownField(prefix + key); });
	}
	private static InvalidReadingRequestException invalid(String code, String field) { return ReadingInputSupport.invalid(code, field, "입력한 계산 정보를 확인해 주세요."); }
	public record Validated(Map<String,Object> profile, SajuCalculationOptions options, double longitude, String coordinateSource) { }
}
