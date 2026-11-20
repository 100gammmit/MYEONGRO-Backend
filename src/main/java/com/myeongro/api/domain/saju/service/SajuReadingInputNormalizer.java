package com.myeongro.api.domain.saju.service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.myeongro.api.domain.reading.service.ReadingInputSupport;
import com.myeongro.api.domain.reading.service.ReadingSchemaVersions;
import com.myeongro.api.domain.saju.controller.SajuReadingCreateRequest;
import com.myeongro.api.domain.saju.model.BirthTimePrecision;
import com.myeongro.api.domain.saju.model.LuckDirectionBasis;
import com.myeongro.api.domain.saju.model.SajuBirthProfileRequest;
import com.myeongro.api.domain.saju.model.SajuFocusArea;
import com.myeongro.api.domain.saju.calculation.SajuCalculationOptions;
import com.myeongro.api.domain.saju.model.SajuBirthInputRules;
import com.myeongro.api.domain.saju.place.SajuBirthPlaceCatalog;

@Component
public class SajuReadingInputNormalizer {

	private final SajuBirthPlaceCatalog birthPlaceCatalog;

	public SajuReadingInputNormalizer(SajuBirthPlaceCatalog birthPlaceCatalog) {
		this.birthPlaceCatalog = birthPlaceCatalog;
	}

	public SajuCalculationInput normalize(SajuReadingCreateRequest request) {
		return normalize(request, LocalDate.now(com.myeongro.api.domain.saju.calculation.SajuCalculationRules.BIRTH_ZONE).getYear());
	}

	public SajuCalculationInput normalize(SajuReadingCreateRequest request, int currentYear) {
		int targetYear = request.targetYear() == null ? currentYear : request.targetYear();
		if (targetYear < 1900 || targetYear > 2100) {
			throw ReadingInputSupport.invalid("INVALID_TARGET_YEAR", "targetYear", "리딩 연도를 확인해 주세요.");
		}
		return normalize(request, com.myeongro.api.domain.saju.model.SajuReportType.fromValue(request.reportType()).schemaVersion(), targetYear);
	}

	private SajuCalculationInput normalize(
		SajuReadingCreateRequest request,
		int schemaVersion,
		int targetYear
	) {
		var reportType = com.myeongro.api.domain.saju.model.SajuReportType.fromValue(request.reportType());
		String question = reportType == com.myeongro.api.domain.saju.model.SajuReportType.ANNUAL_MONTHLY
			? (request.question() == null ? "" : request.question().trim()) : ReadingInputSupport.normalizeQuestion(request.question());
		if (question.length() > 300) throw ReadingInputSupport.invalid("QUESTION_TOO_LONG", "question", "질문은 300자 이하로 입력해 주세요.");
		SajuBirthProfileRequest profile = request.birthProfile();
		if (profile == null) {
			throw ReadingInputSupport.invalid(
				"INVALID_BIRTH_DATE", "birthProfile", "출생정보를 입력해 주세요."
			);
		}
		if (!"solar".equals(profile.calendarType())) {
			throw ReadingInputSupport.invalid(
				"UNSUPPORTED_CALENDAR_TYPE",
				"birthProfile.calendarType",
				"현재는 양력 생년월일만 지원합니다."
			);
		}

		LocalDate birthDate = parseBirthDate(profile.birthDate());
		BirthTimePrecision precision = BirthTimePrecision.fromValue(profile.birthTimePrecision());
		String birthTime = normalizeBirthTime(profile.birthTime(), precision);
		LuckDirectionBasis direction = LuckDirectionBasis.fromValue(profile.luckDirectionBasis());
		SajuFocusArea focusArea = SajuFocusArea.fromValue(request.focusArea());
		boolean birthPlaceRequired = precision != BirthTimePrecision.UNKNOWN;
		if (!birthPlaceRequired
			&& schemaVersion >= ReadingSchemaVersions.SAJU
			&& profile.provinceCode() != null) {
			throw ReadingInputSupport.invalid(
				"INVALID_BIRTH_PLACE",
				"birthProfile.provinceCode",
				"출생시간을 모르는 경우 출생 시·도를 비워 주세요."
			);
		}
		if (!birthPlaceRequired && profile.cityCode() != null) {
			throw ReadingInputSupport.invalid(
				"INVALID_BIRTH_PLACE",
				"birthProfile.cityCode",
				"출생시간을 모르는 경우 출생 시·군·구를 비워 주세요."
			);
		}
		if (birthPlaceRequired) {
			birthPlaceCatalog.requireProvince(profile.provinceCode());
			if (profile.cityCode() != null) {
				birthPlaceCatalog.requireCity(profile.provinceCode(), profile.cityCode());
			}
		}
		SajuCalculationOptions options = SajuBirthInputRules.parseCalculationOptions(
			request.calculationOptions(), "calculationOptions"
		);

		Map<String, Object> normalizedProfile = new LinkedHashMap<>();
		normalizedProfile.put("calendarType", "solar");
		normalizedProfile.put("birthDate", birthDate.toString());
		if (birthTime != null) {
			normalizedProfile.put("birthTime", birthTime);
		}
		normalizedProfile.put("birthTimePrecision", precision.value());
		if (birthPlaceRequired) {
			normalizedProfile.put("provinceCode", profile.provinceCode());
			if (profile.cityCode() != null) {
				normalizedProfile.put("cityCode", profile.cityCode());
			}
		}
		normalizedProfile.put("luckDirectionBasis", direction.value());
		// Part of the idempotency payload, so a retry that changes only an option is a different request. Defaults are
		// left out: an omitted and an explicit default are the same request, and pre-B2 fingerprints stay unchanged.
		if (options.dayBoundary() != SajuCalculationOptions.DEFAULT.dayBoundary()) {
			normalizedProfile.put("dayBoundary", options.dayBoundary().name());
		}
		if (options.equationOfTime() != SajuCalculationOptions.DEFAULT.equationOfTime()) {
			normalizedProfile.put("equationOfTime", options.equationOfTime());
		}

		return new SajuCalculationInput(
			schemaVersion,
			question,
			focusArea,
			ReadingInputSupport.immutable(normalizedProfile),
			targetYear, reportType
		);
	}

	private LocalDate parseBirthDate(String value) {
		return com.myeongro.api.domain.saju.model.SajuBirthInputRules.parseBirthDate(value);
	}
	private String normalizeBirthTime(String value, BirthTimePrecision precision) {
		return com.myeongro.api.domain.saju.model.SajuBirthInputRules.normalizeBirthTime(value, precision);
	}
}
