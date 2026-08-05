package com.myeongro.api.domain.saju.calculation;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Component;

@Component
public class TrueSolarTimeCorrector {

	public Correction correct(LocalDateTime civilTime, double longitude) {
		ZoneOffset offset = civilTime.atZone(SajuCalculationRules.BIRTH_ZONE).getOffset();
		double standardMeridian = offset.getTotalSeconds() / 3600d * 15d;
		double longitudeMinutes = (longitude - standardMeridian) * 4d;
		double equationMinutes = equationOfTimeMinutes(civilTime.getDayOfYear());
		long totalSeconds = Math.round((longitudeMinutes + equationMinutes) * 60d);
		return new Correction(
			civilTime,
			civilTime.plusSeconds(totalSeconds),
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
		String zoneOffset,
		double longitudeCorrectionMinutes,
		double equationOfTimeMinutes,
		long totalCorrectionSeconds
	) {
	}
}
