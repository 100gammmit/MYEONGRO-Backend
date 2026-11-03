package com.myeongro.api.domain.saju.service;

import java.util.Map;
import java.util.LinkedHashMap;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.service.PreparedReadingInput;
import com.myeongro.api.domain.saju.calculation.SajuMonthlyCalendarService;
import com.myeongro.api.domain.saju.calculation.SajuCalculationService;
import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot;

@Component
public class SajuReadingInputAssembler {

	private final SajuCalculationService calculationService;
	private final SajuInterpretationInputMapper interpretationInputMapper;
	private final SajuStoredCalculationMapper storedCalculationMapper;
	private final ObjectMapper objectMapper;
	private final SajuMonthlyCalendarService monthlyCalendarService;

	public SajuReadingInputAssembler(
		SajuCalculationService calculationService,
		SajuInterpretationInputMapper interpretationInputMapper,
		SajuStoredCalculationMapper storedCalculationMapper,
		ObjectMapper objectMapper
	) {
		this(calculationService, interpretationInputMapper, storedCalculationMapper, objectMapper, new SajuMonthlyCalendarService());
	}

	@Autowired
	public SajuReadingInputAssembler(SajuCalculationService calculationService,
		SajuInterpretationInputMapper interpretationInputMapper, SajuStoredCalculationMapper storedCalculationMapper,
		ObjectMapper objectMapper, SajuMonthlyCalendarService monthlyCalendarService) {
		this.calculationService = calculationService;
		this.interpretationInputMapper = interpretationInputMapper;
		this.storedCalculationMapper = storedCalculationMapper;
		this.objectMapper = objectMapper;
		this.monthlyCalendarService = monthlyCalendarService;
	}

	public PreparedReadingInput assemble(SajuCalculationInput input, int targetYear) {
		if (input.targetYear() != targetYear) throw new IllegalArgumentException("Target year mismatch");
		SajuCalculationSnapshot snapshot = calculationService.calculate(
			input.birthProfile(), targetYear
		);
		Map<String, Object> calculated = objectMapper.convertValue(
			snapshot, new TypeReference<Map<String, Object>>() {
			}
		);
		SajuAiInput aiInput = new SajuAiInput(
			input.question(),
			input.focusArea().value(),
			targetYear,
			interpretationInputMapper.map(calculated, targetYear)
		);
		Map<String, Object> storedCalculation = new LinkedHashMap<>(storedCalculationMapper.map(snapshot));
		storedCalculation.put("monthlyCalendar", objectMapper.convertValue(
			monthlyCalendarService.calculate(targetYear, snapshot.dayMaster()),
			new TypeReference<Map<String, Object>>() { }
		));
		SajuStoredInput storedInput = new SajuStoredInput(targetYear, storedCalculation);
		return new PreparedReadingInput(
			ReadingKind.SAJU,
			null,
			input.schemaVersion(),
			aiInput.question(),
			aiInput.payload(),
			storedInput.payload()
		);
	}
}
