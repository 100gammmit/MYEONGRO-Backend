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

	public PreparedReadingInput assemble(SajuCalculationInput input, int targetYear, Runnable checkDeadline) {
		if (input.targetYear() != targetYear) throw new IllegalArgumentException("Target year mismatch");
		SajuCalculationSnapshot snapshot = calculationService.calculate(
			input.birthProfile(), targetYear, checkDeadline
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
		// The annual report sends this calculation to the model; the basis is added only to the stored copy.
		Map<String, Object> modelCalculation = new LinkedHashMap<>(storedCalculationMapper.map(snapshot));
		modelCalculation.put("monthlyCalendar", objectMapper.convertValue(
			monthlyCalendarService.calculate(targetYear, snapshot.dayMaster()),
			new TypeReference<Map<String, Object>>() { }
		));
		Map<String, Object> storedCalculation = new LinkedHashMap<>(modelCalculation);
		storedCalculation.put("calculationBasis", calculationBasis(input.birthProfile()));
		SajuStoredInput storedInput = new SajuStoredInput(targetYear, storedCalculation);
		if (input.reportType() == com.myeongro.api.domain.saju.model.SajuReportType.ANNUAL_MONTHLY) {
			Map<String, Object> stored = new LinkedHashMap<>(storedInput.payload());
			stored.put("reportType", input.reportType().value());
			return new PreparedReadingInput(ReadingKind.SAJU, null, input.schemaVersion(), input.question(),
				Map.of("reportType", input.reportType().value(), "targetYear", targetYear,
					"focusArea", input.focusArea().value(), "calculationSnapshot", modelCalculation), stored);
		}
		return new PreparedReadingInput(
			ReadingKind.SAJU,
			null,
			input.schemaVersion(),
			aiInput.question(),
			aiInput.payload(),
			storedInput.payload()
		);
	}

	/**
	 * Which rules produced the stored chart. Only the option values and the longitude unit are kept: never the
	 * province or city code, the longitude itself or any raw birth data.
	 */
	static Map<String, Object> calculationBasis(Map<String, Object> birthProfile) {
		var options = SajuCalculationService.options(birthProfile);
		Map<String, Object> basis = new LinkedHashMap<>();
		basis.put("dayBoundary", options.dayBoundary().name());
		basis.put("equationOfTime", options.equationOfTime());
		basis.put("coordinateSource", SajuCalculationService.coordinateSource(birthProfile));
		return java.util.Collections.unmodifiableMap(basis);
	}
}
