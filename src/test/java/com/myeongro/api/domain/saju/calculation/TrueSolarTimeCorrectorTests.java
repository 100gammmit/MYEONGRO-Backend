package com.myeongro.api.domain.saju.calculation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class TrueSolarTimeCorrectorTests {

	private final TrueSolarTimeCorrector corrector = new TrueSolarTimeCorrector();

	@Test
	void usesHistoricalAsiaSeoulOffsetInsteadOfAConstantModernOffset() {
		var historical = corrector.correct(
			LocalDateTime.parse("1960-01-01T12:00"), 127.0
		);
		var modern = corrector.correct(
			LocalDateTime.parse("1990-01-01T12:00"), 127.0
		);

		assertThat(historical.zoneOffset()).isEqualTo("+08:30");
		assertThat(modern.zoneOffset()).isEqualTo("+09:00");
		assertThat(historical.longitudeCorrectionMinutes()).isCloseTo(-2.0, within(0.001));
		assertThat(modern.longitudeCorrectionMinutes()).isCloseTo(-32.0, within(0.001));
	}

	@Test
	void combinesLongitudeAndEquationOfTimeIntoOneDeterministicCorrection() {
		var correction = corrector.correct(
			LocalDateTime.parse("1992-08-17T12:00"), 127.2647
		);

		assertThat(correction.longitudeCorrectionMinutes()).isEqualTo(-30.941);
		assertThat(correction.trueSolarTime()).isBefore(correction.civilTime());
		assertThat(correction.totalCorrectionSeconds()).isEqualTo(
			Math.round((correction.longitudeCorrectionMinutes()
				+ correction.equationOfTimeMinutes()) * 60d)
		);
	}

	@Test
	void exposesNoCandidateForDstGapAndBothInstantsForOverlap() {
		var gap = corrector.correctCandidates(
			LocalDateTime.parse("1988-05-08T02:30"), 126.978
		);
		var overlap = corrector.correctCandidates(
			LocalDateTime.parse("1988-10-09T02:30"), 126.978
		);

		assertThat(gap).isEmpty();
		assertThat(overlap).hasSize(2);
		assertThat(overlap).extracting(TrueSolarTimeCorrector.Correction::zoneOffset)
			.containsExactly("+10:00", "+09:00");
		assertThat(overlap).extracting(TrueSolarTimeCorrector.Correction::engineCivilTime)
			.containsExactly(
				LocalDateTime.parse("1988-10-09T00:30"),
				LocalDateTime.parse("1988-10-09T01:30")
			);
	}
}
