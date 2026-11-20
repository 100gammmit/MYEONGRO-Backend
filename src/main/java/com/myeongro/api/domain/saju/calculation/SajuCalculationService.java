package com.myeongro.api.domain.saju.calculation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.myeongro.api.domain.saju.calculation.ApproximateBirthTimeResolver.Resolution;
import com.myeongro.api.domain.saju.calculation.LunarJavaFourPillarsAdapter.Candidate;
import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot.TimeCorrection;
import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot.Uncertainty;
import com.myeongro.api.domain.saju.model.BirthTimePrecision;
import com.myeongro.api.domain.saju.model.LuckDirectionBasis;
import com.myeongro.api.domain.saju.place.SajuBirthPlaceCatalog;
import com.myeongro.api.domain.reading.exception.InvalidReadingRequestException;
import com.myeongro.api.domain.reading.exception.ReadingGenerationExpiredException;

@Service
public class SajuCalculationService {

	private final SajuBirthPlaceCatalog birthPlaceCatalog;
	private final TrueSolarTimeCorrector corrector;
	private final ApproximateBirthTimeResolver approximateResolver;

	public SajuCalculationService(
		SajuBirthPlaceCatalog birthPlaceCatalog,
		TrueSolarTimeCorrector corrector,
		ApproximateBirthTimeResolver approximateResolver
	) {
		this.birthPlaceCatalog = birthPlaceCatalog;
		this.corrector = corrector;
		this.approximateResolver = approximateResolver;
	}

	public SajuCalculationSnapshot calculate(Map<String, Object> birthProfile, int targetYear) {
		return calculate(birthProfile, targetYear, () -> {});
	}

	public SajuCalculationSnapshot calculate(
		Map<String, Object> birthProfile, int targetYear, Runnable checkDeadline
	) {
		try {
			return doCalculate(birthProfile, targetYear, checkDeadline);
		} catch (SajuCalculationException | InvalidReadingRequestException | ReadingGenerationExpiredException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new SajuCalculationException(exception);
		}
	}

	private SajuCalculationSnapshot doCalculate(
		Map<String, Object> birthProfile,
		int targetYear,
		Runnable checkDeadline
	) {
		// saju-ko-v5: AI readings use the same precise correction, longitude unit and options as the public manseryeok,
		// so the same input gives the same chart on both. Stored v1-v4 readings are never recalculated.
		return calculateForChart(birthProfile, targetYear, longitude(birthPlaceCatalog, birthProfile), settings(birthProfile),
			SajuCandidateObserver.NONE, checkDeadline);
	}

	/** Longitude unit used for a normalized profile: {@code reference} (time unknown), {@code city} or {@code province}. */
	public static String coordinateSource(Map<String, Object> birthProfile) {
		if (BirthTimePrecision.fromValue((String)birthProfile.get("birthTimePrecision")) == BirthTimePrecision.UNKNOWN) {
			return "reference";
		}
		return birthProfile.get("cityCode") instanceof String ? "city" : "province";
	}

	/** Options carried by a normalized profile; profiles normalized before B2 carry none and get the defaults. */
	public static SajuCalculationOptions options(Map<String, Object> birthProfile) {
		Object boundary = birthProfile.get("dayBoundary");
		Object equation = birthProfile.get("equationOfTime");
		return new SajuCalculationOptions(
			boundary instanceof String text ? SajuCalculationOptions.DayBoundary.valueOf(text)
				: SajuCalculationOptions.DEFAULT.dayBoundary(),
			equation instanceof Boolean flag ? flag : SajuCalculationOptions.DEFAULT.equationOfTime()
		);
	}

	/**
	 * Longitude for a normalized profile. The single rule shared by AI readings and the public manseryeok, so the same
	 * input always gets the same longitude on both. Validates the province and city codes against the catalog.
	 */
	public static double longitude(SajuBirthPlaceCatalog catalog, Map<String, Object> birthProfile) {
		return switch (coordinateSource(birthProfile)) {
			case "reference" -> SajuCalculationRules.KOREA_REFERENCE_LONGITUDE;
			case "city" -> catalog.requireCity(
				(String)birthProfile.get("provinceCode"), (String)birthProfile.get("cityCode")).longitude();
			default -> catalog.requireProvince((String)birthProfile.get("provinceCode")).longitude();
		};
	}

	private static SajuCalculationSettings settings(Map<String, Object> birthProfile) {
		SajuCalculationOptions options = options(birthProfile);
		return options.equals(SajuCalculationOptions.DEFAULT) ? AI_READING_SETTINGS : SajuCalculationSettings.precise(options);
	}

	static final SajuCalculationSettings AI_READING_SETTINGS = SajuCalculationSettings.precise(SajuCalculationOptions.DEFAULT);

	/** Internal calculation carrier only. Public DTO owns its own version; never persist this as a legacy AI snapshot. */
	public SajuCalculationSnapshot calculateForChart(Map<String, Object> birthProfile, int targetYear, double longitude,
		SajuCalculationSettings settings, SajuCandidateObserver observer, Runnable checkDeadline) {
		LocalDate birthDate = LocalDate.parse(requiredString(birthProfile, "birthDate"));
		BirthTimePrecision precision = BirthTimePrecision.fromValue(
			requiredString(birthProfile, "birthTimePrecision")
		);
		LuckDirectionBasis luckBasis = LuckDirectionBasis.fromValue(
			requiredString(birthProfile, "luckDirectionBasis")
		);

		Candidate candidate;
		TimeCorrection correction = null;
		Uncertainty uncertainty;
		List<SajuLimitationCode> limitations = new ArrayList<>();
		if (precision == BirthTimePrecision.UNKNOWN) {
			Resolution resolution = approximateResolver.resolveUnknown(
				birthDate, longitude, luckBasis, targetYear, checkDeadline, settings, observer
			);
			candidate = resolution.trusted();
			limitations.addAll(resolution.limitations());
			limitations.add(SajuLimitationCode.BIRTH_TIME_UNKNOWN);
			limitations.add(SajuLimitationCode.TIME_PILLAR_UNCERTAIN);
			limitations.add(SajuLimitationCode.LUCK_CYCLE_UNCERTAIN);
			List<String> varying = new ArrayList<>(resolution.uncertainty().varyingFields());
			varying.add("pillars.time");
			varying.add("luckCycle");
			uncertainty = new Uncertainty(
				"unknown", resolution.uncertainty().candidateCount(),
				resolution.uncertainty().rangeStart(), resolution.uncertainty().rangeEnd(),
				varying.stream().distinct().toList(),
				resolution.uncertainty().candidateZoneOffsets()
			);
		} else {
			LocalTime birthTime = LocalTime.parse(requiredString(birthProfile, "birthTime"));
			LocalDateTime civilTime = birthDate.atTime(birthTime);
			if (precision == BirthTimePrecision.APPROXIMATE) {
				var centerCorrections = corrections(civilTime, longitude, settings);
				Resolution resolution = approximateResolver.resolve(
					civilTime, longitude, luckBasis, targetYear, checkDeadline, settings, observer
				);
				candidate = resolution.trusted();
				correction = timeCorrection(centerCorrections);
				limitations.addAll(resolution.limitations());
				uncertainty = resolution.uncertainty();
			} else {
				var corrections = corrections(civilTime, longitude, settings);
				if (corrections.isEmpty()) {
					throw new InvalidReadingRequestException(
						"INVALID_BIRTH_TIME", "birthProfile.birthTime",
						"해당 출생시각은 당시 표준시 변경으로 존재하지 않습니다."
					);
				}
				Resolution resolution = approximateResolver.resolveExact(
					corrections, luckBasis, targetYear, checkDeadline, settings, observer
				);
				candidate = resolution.trusted();
				correction = timeCorrection(corrections);
				limitations.addAll(resolution.limitations());
				uncertainty = resolution.uncertainty();
			}
		}
		if (luckBasis == LuckDirectionBasis.UNSPECIFIED) {
			limitations.add(SajuLimitationCode.LUCK_DIRECTION_UNSPECIFIED);
		}

		return new SajuCalculationSnapshot(
			settings.correctionModel() == SajuCalculationSettings.CorrectionModel.LEGACY_DAY_OF_YEAR
				? SajuCalculationRules.LEGACY_DAY_OF_YEAR_VERSION : SajuCalculationRules.CALCULATION_VERSION,
			SajuCalculationRules.ENGINE,
			SajuCalculationRules.ENGINE_VERSION,
			birthPlaceCatalog.version(),
			targetYear,
			correction,
			SajuKoreanTerms.pillars(candidate.pillars()),
			SajuKoreanTerms.stem(candidate.dayMaster()),
			candidate.fiveElements(),
			SajuKoreanTerms.relations(candidate.relations()),
			SajuKoreanTerms.luckCycle(candidate.luckCycle()),
			SajuKoreanTerms.annualFortune(candidate.annualFortune()),
			limitations.stream().distinct().map(Enum::name).toList(),
			uncertainty
		);
	}

	private TimeCorrection timeCorrection(
		List<TrueSolarTimeCorrector.Correction> corrections
	) {
		if (corrections.size() != 1) {
			return null;
		}
		var corrected = corrections.getFirst();
		return new TimeCorrection(
			corrected.civilTime().toString(), corrected.trueSolarTime().toString(),
			corrected.engineCivilTime().toString(), corrected.zoneOffset(),
			corrected.longitudeCorrectionMinutes(), corrected.equationOfTimeMinutes()
		);
	}

	private List<TrueSolarTimeCorrector.Correction> corrections(LocalDateTime civil, double longitude, SajuCalculationSettings settings) {
		return settings == SajuCalculationSettings.LEGACY ? corrector.correctCandidates(civil, longitude)
			: corrector.correctCandidates(civil, longitude, settings);
	}

	private String requiredString(Map<String, Object> values, String key) {
		Object value = values.get(key);
		if (value instanceof String text && !text.isBlank()) {
			return text;
		}
		throw new IllegalArgumentException("Stored birth profile is invalid");
	}
}
