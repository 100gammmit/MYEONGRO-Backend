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
import com.myeongro.api.domain.saju.place.SajuBirthPlace;
import com.myeongro.api.domain.saju.place.SajuBirthPlaceCatalog;

@Service
public class SajuCalculationService {

	private final SajuBirthPlaceCatalog birthPlaceCatalog;
	private final TrueSolarTimeCorrector corrector;
	private final LunarJavaFourPillarsAdapter adapter;
	private final ApproximateBirthTimeResolver approximateResolver;

	public SajuCalculationService(
		SajuBirthPlaceCatalog birthPlaceCatalog,
		TrueSolarTimeCorrector corrector,
		LunarJavaFourPillarsAdapter adapter,
		ApproximateBirthTimeResolver approximateResolver
	) {
		this.birthPlaceCatalog = birthPlaceCatalog;
		this.corrector = corrector;
		this.adapter = adapter;
		this.approximateResolver = approximateResolver;
	}

	public SajuCalculationSnapshot calculate(Map<String, Object> birthProfile, int targetYear) {
		try {
			return doCalculate(birthProfile, targetYear);
		} catch (SajuCalculationException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new SajuCalculationException(exception);
		}
	}

	private SajuCalculationSnapshot doCalculate(
		Map<String, Object> birthProfile,
		int targetYear
	) {
		LocalDate birthDate = LocalDate.parse(requiredString(birthProfile, "birthDate"));
		BirthTimePrecision precision = BirthTimePrecision.fromValue(
			requiredString(birthProfile, "birthTimePrecision")
		);
		LuckDirectionBasis luckBasis = LuckDirectionBasis.fromValue(
			requiredString(birthProfile, "luckDirectionBasis")
		);
		SajuBirthPlace place = birthPlaceCatalog.require(
			requiredString(birthProfile, "provinceCode"),
			requiredString(birthProfile, "cityCode")
		);

		Candidate candidate;
		TimeCorrection correction = null;
		Uncertainty uncertainty;
		List<SajuLimitationCode> limitations = new ArrayList<>();
		if (precision == BirthTimePrecision.UNKNOWN) {
			Resolution resolution = approximateResolver.resolveUnknown(
				birthDate, place.longitude(), luckBasis, targetYear
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
				varying.stream().distinct().toList()
			);
		} else {
			LocalTime birthTime = LocalTime.parse(requiredString(birthProfile, "birthTime"));
			LocalDateTime civilTime = birthDate.atTime(birthTime);
			if (precision == BirthTimePrecision.APPROXIMATE) {
				var corrected = corrector.correct(civilTime, place.longitude());
				Resolution resolution = approximateResolver.resolve(
					civilTime, place.longitude(), luckBasis, targetYear
				);
				candidate = resolution.trusted();
				correction = new TimeCorrection(
					corrected.civilTime().toString(), corrected.trueSolarTime().toString(),
					corrected.zoneOffset(), corrected.longitudeCorrectionMinutes(),
					corrected.equationOfTimeMinutes()
				);
				limitations.addAll(resolution.limitations());
				uncertainty = resolution.uncertainty();
			} else {
				var corrected = corrector.correct(civilTime, place.longitude());
				candidate = adapter.calculate(corrected.trueSolarTime(), true, luckBasis, targetYear);
				correction = new TimeCorrection(
					corrected.civilTime().toString(), corrected.trueSolarTime().toString(),
					corrected.zoneOffset(), corrected.longitudeCorrectionMinutes(),
					corrected.equationOfTimeMinutes()
				);
				uncertainty = new Uncertainty("exact", 1, civilTime.toString(), civilTime.toString(), List.of());
			}
		}
		if (luckBasis == LuckDirectionBasis.UNSPECIFIED) {
			limitations.add(SajuLimitationCode.LUCK_DIRECTION_UNSPECIFIED);
		}

		return new SajuCalculationSnapshot(
			SajuCalculationRules.CALCULATION_VERSION,
			SajuCalculationRules.ENGINE,
			SajuCalculationRules.ENGINE_VERSION,
			birthPlaceCatalog.version(),
			targetYear,
			correction,
			candidate.pillars(),
			candidate.dayMaster(),
			candidate.fiveElements(),
			candidate.relations(),
			candidate.luckCycle(),
			candidate.annualFortune(),
			limitations.stream().distinct().map(Enum::name).toList(),
			uncertainty
		);
	}

	private String requiredString(Map<String, Object> values, String key) {
		Object value = values.get(key);
		if (value instanceof String text && !text.isBlank()) {
			return text;
		}
		throw new IllegalArgumentException("Stored birth profile is invalid");
	}
}
