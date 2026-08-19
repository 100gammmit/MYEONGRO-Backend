package com.myeongro.api.domain.saju.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.myeongro.api.domain.reading.dto.CreatedReadingResponse;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.service.NormalizedReadingInput;
import com.myeongro.api.domain.reading.service.ReadingInputSupport;
import com.myeongro.api.domain.reading.service.ReadingSchemaVersions;
import com.myeongro.api.domain.saju.controller.SajuReadingCreateRequest;
import com.myeongro.api.domain.saju.model.BirthTimePrecision;
import com.myeongro.api.domain.saju.model.LuckDirectionBasis;
import com.myeongro.api.domain.saju.model.SajuBirthProfileRequest;
import com.myeongro.api.domain.saju.model.SajuFocusArea;
import com.myeongro.api.domain.saju.place.SajuBirthPlaceCatalog;

@Component
public class SajuReadingInputNormalizer {

	private static final int MINIMUM_BIRTH_YEAR = 1900;
	private static final int MAXIMUM_BIRTH_YEAR = 2099;

	private final SajuBirthPlaceCatalog birthPlaceCatalog;

	public SajuReadingInputNormalizer(SajuBirthPlaceCatalog birthPlaceCatalog) {
		this.birthPlaceCatalog = birthPlaceCatalog;
	}

	public NormalizedReadingInput normalize(SajuReadingCreateRequest request) {
		return normalize(request, ReadingSchemaVersions.SAJU, null);
	}

	public NormalizedReadingInput restoreBase(CreatedReadingResponse reading) {
		if (reading.kind() != ReadingKind.SAJU
			|| reading.spreadType() != null
			|| !ReadingSchemaVersions.supports(reading.kind(), reading.schemaVersion())) {
			throw new IllegalArgumentException("Unsupported saju reading schema version");
		}
		Map<String, Object> payload = reading.input();
		Map<?, ?> profile = ReadingInputSupport.valueAsMap(
			payload.get("birthProfile"), "Stored birth profile"
		);
		boolean legacySajuSchema = reading.schemaVersion() == 2;
		String legacyCityCode = legacySajuSchema
			? ReadingInputSupport.valueAsString(profile.get("cityCode"), "Stored city code")
			: null;
		return normalize(new SajuReadingCreateRequest(
			ReadingInputSupport.valueAsString(payload.get("question"), "Stored question"),
			java.util.UUID.randomUUID(),
			new SajuBirthProfileRequest(
				ReadingInputSupport.valueAsString(profile.get("calendarType"), "Stored calendar type"),
				ReadingInputSupport.valueAsString(profile.get("birthDate"), "Stored birth date"),
				ReadingInputSupport.nullableString(profile.get("birthTime")),
				ReadingInputSupport.valueAsString(
					profile.get("birthTimePrecision"), "Stored birth time precision"
				),
				legacySajuSchema
					? ReadingInputSupport.valueAsString(
						profile.get("provinceCode"), "Stored province code"
					)
					: ReadingInputSupport.nullableString(profile.get("provinceCode")),
				ReadingInputSupport.valueAsString(
					profile.get("luckDirectionBasis"), "Stored luck direction basis"
				)
			),
			ReadingInputSupport.valueAsString(payload.get("focusArea"), "Stored focus area")
		), reading.schemaVersion(), legacyCityCode);
	}

	private NormalizedReadingInput normalize(
		SajuReadingCreateRequest request,
		int schemaVersion,
		String legacyCityCode
	) {
		String question = ReadingInputSupport.normalizeQuestion(request.question());
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
			&& schemaVersion == ReadingSchemaVersions.SAJU
			&& profile.provinceCode() != null) {
			throw ReadingInputSupport.invalid(
				"INVALID_BIRTH_PLACE",
				"birthProfile.provinceCode",
				"출생시간을 모르는 경우 출생 시·도를 비워 주세요."
			);
		}
		if (birthPlaceRequired) {
			birthPlaceCatalog.requireProvince(profile.provinceCode());
			if (legacyCityCode != null) {
				birthPlaceCatalog.require(profile.provinceCode(), legacyCityCode);
			}
		}

		Map<String, Object> normalizedProfile = new LinkedHashMap<>();
		normalizedProfile.put("calendarType", "solar");
		normalizedProfile.put("birthDate", birthDate.toString());
		if (birthTime != null) {
			normalizedProfile.put("birthTime", birthTime);
		}
		normalizedProfile.put("birthTimePrecision", precision.value());
		if (birthPlaceRequired) {
			normalizedProfile.put("provinceCode", profile.provinceCode());
		}
		normalizedProfile.put("luckDirectionBasis", direction.value());

		Map<String, Object> payload = ReadingInputSupport.orderedMap(
			"question", question,
			"focusArea", focusArea.value(),
			"birthProfile", ReadingInputSupport.immutable(normalizedProfile)
		);
		return new NormalizedReadingInput(
			ReadingKind.SAJU,
			null,
			schemaVersion,
			question,
			payload,
			ReadingInputSupport.hashMaterial(ReadingKind.SAJU, null, schemaVersion, payload)
		);
	}

	private LocalDate parseBirthDate(String value) {
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

	private String normalizeBirthTime(String value, BirthTimePrecision precision) {
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
