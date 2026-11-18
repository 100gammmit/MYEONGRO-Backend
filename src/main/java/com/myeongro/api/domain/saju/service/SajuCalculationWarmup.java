package com.myeongro.api.domain.saju.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.saju.calculation.SajuCalculationRules;
import com.myeongro.api.domain.saju.calculation.SajuCalculationService;
import com.myeongro.api.domain.saju.controller.SajuCalculationRequest;

/**
 * Runs each saju calculation path once at startup with a fixed synthetic birth profile, so the first real request
 * does not pay for class loading and JIT warm-up. No user data is involved and results are discarded unlogged.
 */
@Component
@ConditionalOnProperty(name = "app.saju.calculation-warmup.enabled", havingValue = "true", matchIfMissing = true)
public class SajuCalculationWarmup implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(SajuCalculationWarmup.class);
	private static final List<Map<String, Object>> LEGACY_PROFILES = List.of(
		Map.of("calendarType", "solar", "birthDate", "2000-06-15", "birthTime", "14:30",
			"birthTimePrecision", "exact", "provinceCode", "11", "luckDirectionBasis", "male"),
		Map.of("calendarType", "solar", "birthDate", "2000-06-15", "birthTime", "14:30",
			"birthTimePrecision", "approximate", "provinceCode", "11", "luckDirectionBasis", "female"),
		Map.of("calendarType", "solar", "birthDate", "2000-06-15",
			"birthTimePrecision", "unknown", "luckDirectionBasis", "unspecified")
	);

	private final SajuCalculationService legacy;
	private final ManseryeokCalculationService manseryeok;
	private final ObjectMapper mapper;

	public SajuCalculationWarmup(SajuCalculationService legacy, ManseryeokCalculationService manseryeok, ObjectMapper mapper) {
		this.legacy = legacy;
		this.manseryeok = manseryeok;
		this.mapper = mapper;
	}

	@Override
	public void run(ApplicationArguments args) {
		long started = System.nanoTime();
		int targetYear = LocalDate.now(SajuCalculationRules.BIRTH_ZONE).getYear();
		try {
			for (Map<String, Object> profile : LEGACY_PROFILES) {
				legacy.calculate(profile, targetYear);
				manseryeok.calculate(mapper.convertValue(Map.of("birthProfile", profile), SajuCalculationRequest.class));
			}
			log.info("Saju calculation warm-up finished in {} ms", (System.nanoTime() - started) / 1_000_000);
		} catch (RuntimeException exception) {
			// Warm-up is an optimisation only; never block startup or log the synthetic input.
			log.warn("Saju calculation warm-up skipped: {}", exception.getClass().getSimpleName());
		}
	}
}
