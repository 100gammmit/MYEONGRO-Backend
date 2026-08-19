package com.myeongro.api.domain.saju.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
		int targetYear = LocalDate.ofInstant(
			clock.instant(), SajuCalculationRules.BIRTH_ZONE
		).getYear();
		return workflow.create(
			userId,
			requestId,
			() -> inputNormalizer.normalize(request),
			input -> inputAssembler.assemble(input, targetYear)
		);
	}
}
