package com.myeongro.api.domain.saju.calculation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.myeongro.api.domain.saju.calculation.LunarJavaFourPillarsAdapter.Candidate;
import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot.AnnualFortune;
import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot.Pillar;
import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot.Pillars;
import com.myeongro.api.domain.saju.model.LuckDirectionBasis;

class ApproximateBirthTimeResolverTests {

	@Test
	void evaluatesEveryMinuteAndKeepsOnlyFactsSharedByAllCandidates() {
		TrueSolarTimeCorrector corrector = org.mockito.Mockito.mock(TrueSolarTimeCorrector.class);
		LunarJavaFourPillarsAdapter adapter = org.mockito.Mockito.mock(LunarJavaFourPillarsAdapter.class);
		when(corrector.correct(any(), anyDouble())).thenAnswer(invocation -> {
			LocalDateTime value = invocation.getArgument(0);
			return new TrueSolarTimeCorrector.Correction(value, value, "+09:00", 0, 0, 0);
		});
		when(adapter.calculate(any(), org.mockito.ArgumentMatchers.eq(true), any(), anyInt()))
			.thenAnswer(invocation -> candidate(invocation.<LocalDateTime>getArgument(0).getMinute()));

		var resolution = new ApproximateBirthTimeResolver(corrector, adapter).resolve(
			LocalDateTime.parse("1992-08-17T12:00"),
			127.0,
			LuckDirectionBasis.MALE,
			2026
		);

		assertThat(resolution.uncertainty().candidateCount()).isEqualTo(121);
		assertThat(resolution.trusted().pillars().year().ganZhi()).isEqualTo("壬申");
		assertThat(resolution.trusted().pillars().time()).isNull();
		assertThat(resolution.uncertainty().varyingFields()).contains("pillars.time");
		verify(adapter, times(121)).calculate(any(), org.mockito.ArgumentMatchers.eq(true), any(), anyInt());
	}

	private Candidate candidate(int minute) {
		Pillar year = pillar("壬申");
		Pillar month = pillar("戊申");
		Pillar day = pillar("乙丑");
		Pillar time = pillar(minute == 30 ? "甲午" : "壬午");
		return new Candidate(
			new Pillars(year, month, day, time),
			"乙",
			Map.of("wood", 1, "fire", 1),
			List.of(),
			null,
			new AnnualFortune(2026, "丙午", "伤官")
		);
	}

	private Pillar pillar(String ganZhi) {
		return new Pillar(
			ganZhi, ganZhi.substring(0, 1), ganZhi.substring(1),
			"", "", List.of()
		);
	}
}
