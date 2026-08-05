package com.myeongro.api.domain.saju.calculation;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class TrueSolarTimeCorrector {

	public Correction correct(LocalDateTime civilTime, double longitude) {
		List<Correction> candidates = correctCandidates(civilTime, longitude);
		if (candidates.size() != 1) {
			throw new IllegalArgumentException("Civil time is missing or ambiguous");
		}
		return candidates.getFirst();
	}

	public List<Correction> correctCandidates(LocalDateTime civilTime, double longitude) {
		return SajuCalculationRules.BIRTH_ZONE.getRules().getValidOffsets(civilTime).stream()
			.map(offset -> correct(civilTime, longitude, offset))
			.toList();
	}

	private Correction correct(
		LocalDateTime civilTime,
		double longitude,
		ZoneOffset offset
	) {
		double standardMeridian = offset.getTotalSeconds() / 3600d * 15d;
		double longitudeMinutes = (longitude - standardMeridian) * 4d;
		double equationMinutes = equationOfTimeMinutes(civilTime.getDayOfYear());
		long totalSeconds = Math.round((longitudeMinutes + equationMinutes) * 60d);
		return new Correction(
			civilTime,
			civilTime.plusSeconds(totalSeconds),
			civilTime.toInstant(offset)
				.atOffset(SajuCalculationRules.ENGINE_OFFSET)
				.toLocalDateTime(),
			offset.toString(),
			round(longitudeMinutes),
			round(equationMinutes),
			totalSeconds
		);
	}

	double equationOfTimeMinutes(int dayOfYear) {
		double angle = Math.toRadians((360d / 365d) * (dayOfYear - 81));
		return 9.87d * Math.sin(2d * angle)
			- 7.53d * Math.cos(angle)
			- 1.5d * Math.sin(angle);
	}

	private double round(double value) {
		return Math.round(value * 1000d) / 1000d;
	}

	public record Correction(
		LocalDateTime civilTime,
		LocalDateTime trueSolarTime,
		LocalDateTime engineCivilTime,
		String zoneOffset,
		double longitudeCorrectionMinutes,
		double equationOfTimeMinutes,
		long totalCorrectionSeconds
	) {
	}
}
