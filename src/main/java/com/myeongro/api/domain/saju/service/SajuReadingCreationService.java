package com.myeongro.api.domain.saju.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.myeongro.api.domain.consent.entity.ConsentScope;
import com.myeongro.api.domain.reading.dto.CreatedReadingResponse;
import com.myeongro.api.domain.reading.service.ReadingCreationWorkflow;
import com.myeongro.api.domain.saju.calculation.SajuCalculationRules;
import com.myeongro.api.domain.saju.controller.SajuReadingCreateRequest;

@Service
public class SajuReadingCreationService {

	private final ReadingCreationWorkflow workflow;
	private final SajuReadingInputNormalizer inputNormalizer;
	private final SajuReadingInputAssembler inputAssembler;
	private final Clock clock;

	@Autowired
	public SajuReadingCreationService(
		ReadingCreationWorkflow workflow,
		SajuReadingInputNormalizer inputNormalizer,
		SajuReadingInputAssembler inputAssembler
	) {
		this(workflow, inputNormalizer, inputAssembler, Clock.systemUTC());
	}

	public SajuReadingCreationService(
		ReadingCreationWorkflow workflow,
		SajuReadingInputNormalizer inputNormalizer,
		SajuReadingInputAssembler inputAssembler,
		Clock clock
	) {
		this.workflow = workflow;
		this.inputNormalizer = inputNormalizer;
		this.inputAssembler = inputAssembler;
		this.clock = clock;
	}

	public CreatedReadingResponse create(
		UUID userId,
		UUID requestId,
		SajuReadingCreateRequest request
	) {
		int currentYear = LocalDate.ofInstant(
			clock.instant(), SajuCalculationRules.BIRTH_ZONE
		).getYear();
		return workflow.create(
			userId,
			requestId,
			ConsentScope.SAJU,
			() -> inputNormalizer.normalize(request, currentYear),
			input -> {
				// Existing completed requests replay before this gate, including across New Year.
				if (input.targetYear() != currentYear && input.targetYear() != currentYear + 1) {
					throw com.myeongro.api.domain.reading.service.ReadingInputSupport.invalid(
						"INVALID_TARGET_YEAR", "targetYear", "리딩 연도는 올해 또는 내년을 선택해 주세요."
					);
				}
				return inputAssembler.assemble(input, input.targetYear());
			}
		);
	}
}
