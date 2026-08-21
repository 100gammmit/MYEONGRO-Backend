package com.myeongro.api.domain.readingcredit.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.validation.annotation.Validated;

import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.tarot.model.TarotSpreadType;

import jakarta.validation.Valid;
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
	@NotBlank String cleanupCron
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

	public record Costs(
		@Valid @NotNull Tarot tarot,
		@Positive int saju
	) {
	}

	public record Tarot(
		@Positive int dailyOneCard,
		@Positive int mindThreeCard,
		@Positive int relationshipThreeCard,
		@Positive int choiceFiveCard
	) {
		public int cost(TarotSpreadType spreadType) {
			return switch (spreadType) {
				case DAILY_ONE_CARD -> dailyOneCard;
				case MIND_THREE_CARD -> mindThreeCard;
				case RELATIONSHIP_THREE_CARD -> relationshipThreeCard;
				case CHOICE_FIVE_CARD -> choiceFiveCard;
			};
		}
	}
}
