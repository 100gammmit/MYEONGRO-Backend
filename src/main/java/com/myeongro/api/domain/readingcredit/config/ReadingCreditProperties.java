package com.myeongro.api.domain.readingcredit.config;

import java.time.Duration;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.validation.annotation.Validated;

import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.service.PreparedReadingInput;
import com.myeongro.api.domain.saju.annual.SajuAnnualDetailLevel;
import com.myeongro.api.domain.saju.model.SajuReportType;
import com.myeongro.api.domain.tarot.model.TarotSpreadType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Validated
@ConfigurationProperties("app.reading-credits")
public record ReadingCreditProperties(
	@Min(0) int dailyFreeGrant,
	@Valid @NotNull Costs costs,
	@NotNull Duration staleAfter,
	@NotBlank String cleanupCron,
	@Min(0) int globalDailyAttemptLimit,
	@Min(1) @Max(100) int usageAlertPercent
) {

	public ReadingCreditProperties {
		if (staleAfter == null || staleAfter.isZero() || staleAfter.isNegative()) {
			throw new IllegalArgumentException("Reading stale duration must be positive");
		}
		CronExpression.parse(cleanupCron);
	}

	public int cost(ReadingKind kind, String spreadType) {
		return switch (kind) {
			case TAROT -> costs.tarot().cost(TarotSpreadType.fromValue(spreadType));
			case SAJU -> costs.saju();
		};
	}
	/** List price of a report type; for the annual report this is the full report. */
	public int cost(ReadingKind kind, String spreadType, String reportType) {
		if (isAnnual(kind, reportType)) {
			return costs.sajuAnnual();
		}
		return cost(kind, spreadType);
	}

	/**
	 * Balance needed before the chart is calculated. The annual format is only known afterwards, so the cheaper format
	 * is checked here and {@link #cost(String, PreparedReadingInput)} sets the amount that is recorded and charged.
	 */
	public int preparationCost(ReadingKind kind, String spreadType, String reportType) {
		if (isAnnual(kind, reportType)) {
			return Math.min(costs.sajuAnnual(), costs.sajuAnnualBrief());
		}
		return cost(kind, spreadType);
	}

	/** Amount recorded on the pending reading and charged when it completes, from the request and its calculation. */
	public int cost(String reportType, PreparedReadingInput prepared) {
		if (!isAnnual(prepared.kind(), reportType)) {
			return cost(prepared.kind(), prepared.spreadType());
		}
		if (!(prepared.aiPayload().get("calculationSnapshot") instanceof Map<?, ?> calculation)) {
			throw new IllegalArgumentException("Annual calculation is required");
		}
		return annualCost(SajuAnnualDetailLevel.ofCalculation(calculation));
	}

	public int annualCost(String detailLevel) {
		return switch (detailLevel) {
			case SajuAnnualDetailLevel.FULL -> costs.sajuAnnual();
			case SajuAnnualDetailLevel.BRIEF -> costs.sajuAnnualBrief();
			default -> throw new IllegalArgumentException("Unknown annual detail level");
		};
	}

	private static boolean isAnnual(ReadingKind kind, String reportType) {
		return kind == ReadingKind.SAJU && SajuReportType.fromValue(reportType) == SajuReportType.ANNUAL_MONTHLY;
	}

	public record Costs(
		@Valid @NotNull Tarot tarot,
		@Positive int saju,
		@Positive int sajuAnnual,
		@Positive int sajuAnnualBrief
	) {
	}

	public record Tarot(
		@Positive int mindThreeCard,
		@Positive int relationshipThreeCard,
		@Positive int choiceFiveCard
	) {
		public int cost(TarotSpreadType spreadType) {
			return switch (spreadType) {
				case MIND_THREE_CARD -> mindThreeCard;
				case RELATIONSHIP_THREE_CARD -> relationshipThreeCard;
				case CHOICE_FIVE_CARD -> choiceFiveCard;
			};
		}
	}
}
