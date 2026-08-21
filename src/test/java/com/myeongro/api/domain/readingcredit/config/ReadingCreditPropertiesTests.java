package com.myeongro.api.domain.readingcredit.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;

class ReadingCreditPropertiesTests {

	@Test
	void rejectsNonPositiveStaleDuration() {
		assertThatThrownBy(() -> properties(Duration.ZERO, "0 */5 * * * *"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsInvalidCleanupCron() {
		assertThatThrownBy(() -> properties(Duration.ofMinutes(5), "not-a-cron"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void beanValidationRejectsNegativeGrantAndNonPositiveCosts() {
		var invalid = new ReadingCreditProperties(
			-1,
			new ReadingCreditProperties.Costs(
				new ReadingCreditProperties.Tarot(0, 0, 0, 0), 0
			),
			Duration.ofMinutes(5),
			"0 */5 * * * *"
		);
		try (var factory = Validation.buildDefaultValidatorFactory()) {
			var paths = factory.getValidator().validate(invalid).stream()
				.map(violation -> violation.getPropertyPath().toString())
				.toList();
			org.assertj.core.api.Assertions.assertThat(paths)
				.contains(
					"dailyFreeGrant",
					"costs.saju",
					"costs.tarot.dailyOneCard",
					"costs.tarot.mindThreeCard",
					"costs.tarot.relationshipThreeCard",
					"costs.tarot.choiceFiveCard"
				);
		}
	}

	private ReadingCreditProperties properties(Duration staleAfter, String cleanupCron) {
		return new ReadingCreditProperties(
			10,
			new ReadingCreditProperties.Costs(
				new ReadingCreditProperties.Tarot(1, 2, 2, 3), 4
			),
			staleAfter,
			cleanupCron
		);
	}
}
