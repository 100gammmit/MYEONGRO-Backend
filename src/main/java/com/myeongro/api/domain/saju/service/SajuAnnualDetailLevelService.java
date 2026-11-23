package com.myeongro.api.domain.saju.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.myeongro.api.domain.consent.entity.ConsentScope;
import com.myeongro.api.domain.reading.service.ReadingCreationWorkflow;
import com.myeongro.api.domain.readingcredit.config.ReadingCreditProperties;
import com.myeongro.api.domain.saju.annual.SajuAnnualDetailLevel;
import com.myeongro.api.domain.saju.calculation.SajuCalculationRules;
import com.myeongro.api.domain.saju.calculation.SajuCalculationService;
import com.myeongro.api.domain.saju.controller.SajuDetailLevelRequest;
import com.myeongro.api.domain.saju.controller.SajuReadingCreateRequest;
import com.myeongro.api.domain.saju.model.SajuReportType;

/**
 * Tells a signed-in user, before they spend credits, which annual report their birth input gets and what it costs.
 * It normalizes and calculates exactly as a reading would, and stores, charges, logs and sends nothing.
 */
@Service
public class SajuAnnualDetailLevelService {

	public record Result(String detailLevel, int creditCost) { }

	private final ReadingCreationWorkflow workflow;
	private final SajuReadingInputNormalizer normalizer;
	private final SajuCalculationService calculationService;
	private final ReadingCreditProperties creditProperties;
	private final Clock clock;

	@Autowired
	public SajuAnnualDetailLevelService(ReadingCreationWorkflow workflow, SajuReadingInputNormalizer normalizer,
		SajuCalculationService calculationService, ReadingCreditProperties creditProperties) {
		this(workflow, normalizer, calculationService, creditProperties, Clock.systemUTC());
	}

	SajuAnnualDetailLevelService(ReadingCreationWorkflow workflow, SajuReadingInputNormalizer normalizer,
		SajuCalculationService calculationService, ReadingCreditProperties creditProperties, Clock clock) {
		this.workflow = workflow;
		this.normalizer = normalizer;
		this.calculationService = calculationService;
		this.creditProperties = creditProperties;
		this.clock = clock;
	}

	public Result check(UUID userId, SajuDetailLevelRequest request) {
		workflow.requireConsent(userId, ConsentScope.SAJU);
		int currentYear = LocalDate.ofInstant(clock.instant(), SajuCalculationRules.BIRTH_ZONE).getYear();
		// The format depends only on the birth input; the rest is filled so the shared normalizer validates it the same way.
		SajuCalculationInput input = normalizer.normalize(new SajuReadingCreateRequest(
			"", UUID.randomUUID(), request.birthProfile(), "self", currentYear,
			SajuReportType.ANNUAL_MONTHLY.value(), request.calculationOptions()
		), currentYear);
		String detailLevel = SajuAnnualDetailLevel.of(
			calculationService.calculate(input.birthProfile(), currentYear).dayMaster()
		);
		return new Result(detailLevel, creditProperties.annualCost(detailLevel));
	}
}
