package com.myeongro.api.domain.saju.service;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.service.PreparedReadingInput;
import com.myeongro.api.domain.saju.calculation.SajuCalculationRules;
import com.myeongro.api.domain.saju.calculation.SajuCalculationService;
import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot;

@Component
public class SajuReadingInputAssembler {

	private final SajuCalculationService calculationService;
	private final SajuInterpretationInputMapper interpretationInputMapper;
	private final SajuStoredCalculationMapper storedCalculationMapper;
	private final ObjectMapper objectMapper;

	public SajuReadingInputAssembler(
		SajuCalculationService calculationService,
		SajuInterpretationInputMapper interpretationInputMapper,
		SajuStoredCalculationMapper storedCalculationMapper,
		ObjectMapper objectMapper
	) {
		this.calculationService = calculationService;
		this.interpretationInputMapper = interpretationInputMapper;
		this.storedCalculationMapper = storedCalculationMapper;
		this.objectMapper = objectMapper;
	}

	public PreparedReadingInput assemble(SajuCalculationInput input, int targetYear) {
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
		SajuStoredInput storedInput = new SajuStoredInput(
			targetYear,
			storedCalculationMapper.map(snapshot)
		);
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
