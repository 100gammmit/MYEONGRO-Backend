package com.myeongro.api.domain.saju.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.saju.calculation.ApproximateBirthTimeResolver;
import com.myeongro.api.domain.saju.calculation.LunarJavaFourPillarsAdapter;
import com.myeongro.api.domain.saju.calculation.ManseryeokChartBuilder;
import com.myeongro.api.domain.saju.calculation.SajuCalculationService;
import com.myeongro.api.domain.saju.calculation.SajuMonthlyCalendarService;
import com.myeongro.api.domain.saju.calculation.TrueSolarTimeCorrector;
import com.myeongro.api.domain.saju.place.SajuBirthPlaceCatalog;

class SajuCalculationWarmupTests {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Test
	void runsEveryLegacyAndPublicPathWithTheRealEngines() {
		var catalog = new SajuBirthPlaceCatalog(MAPPER, new ClassPathResource("saju/birth-places/kr-admin-v1.json"));
		var corrector = new TrueSolarTimeCorrector();
		var legacy = new SajuCalculationService(catalog, corrector,
			new ApproximateBirthTimeResolver(corrector, new LunarJavaFourPillarsAdapter()));
		var manseryeok = new ManseryeokCalculationService(legacy, catalog, new ManseryeokChartBuilder(),
			new SajuMonthlyCalendarService(), MAPPER);
		var legacySpy = org.mockito.Mockito.spy(legacy);
		var manseryeokSpy = org.mockito.Mockito.spy(manseryeok);

		new SajuCalculationWarmup(legacySpy, manseryeokSpy, MAPPER).afterSingletonsInstantiated();

		verify(legacySpy, times(3)).calculate(anyMap(), anyInt());
		verify(manseryeokSpy, times(3)).calculate(any());
	}

	@Test
	void aFailingCalculationNeverBlocksStartup() {
		var legacy = mock(SajuCalculationService.class);
		when(legacy.calculate(anyMap(), anyInt())).thenThrow(new IllegalStateException("synthetic"));

		assertThatCode(() -> new SajuCalculationWarmup(legacy, mock(ManseryeokCalculationService.class), MAPPER)
			.afterSingletonsInstantiated()).doesNotThrowAnyException();
	}
}
