package com.myeongro.api.domain.saju.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.Semaphore;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.myeongro.api.domain.consent.entity.ConsentScope;
import com.myeongro.api.domain.reading.service.ReadingCreationWorkflow;
import com.myeongro.api.domain.readingcredit.config.ReadingCreditProperties;
import com.myeongro.api.domain.saju.annual.SajuAnnualDetailLevel;
import com.myeongro.api.domain.saju.calculation.SajuCalculationException;
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

	/** No calculation slot or the deadline passed; the caller falls back to showing both prices. */
	public static final class Busy extends RuntimeException {
		Busy() { super(null, null, false, false); }
	}

	private static final long DEADLINE_NANOS = 3_000_000_000L;
	// Like the public manseryeok, a few concurrent full-day searches at most, so checks never crowd out readings.
	private final Semaphore slots = new Semaphore(2);

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
		if (!slots.tryAcquire()) throw new Busy();
		String dayMaster;
		try {
			long deadline = System.nanoTime() + DEADLINE_NANOS;
			dayMaster = calculationService.calculate(input.birthProfile(), currentYear, () -> {
				if (System.nanoTime() >= deadline || Thread.currentThread().isInterrupted()) throw new Busy();
			}).dayMaster();
		} catch (SajuCalculationException exception) {
			// The calculation wraps unexpected exceptions; an expired deadline must still read as busy.
			if (exception.getCause() instanceof Busy busy) throw busy;
			throw exception;
		} finally {
			slots.release();
		}
		String detailLevel = SajuAnnualDetailLevel.of(dayMaster);
		return new Result(detailLevel, creditProperties.annualCost(detailLevel));
	}
}
