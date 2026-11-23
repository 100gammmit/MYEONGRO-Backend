package com.myeongro.api.domain.readingcredit.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;

class ReadingCreditPropertiesTests {

	@Test
	void bindsActiveDefaultPrices() throws Exception {
		var environment = new org.springframework.core.env.StandardEnvironment();
		environment.getPropertySources().remove(org.springframework.core.env.StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
		environment.getPropertySources().remove(org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
		new org.springframework.boot.env.YamlPropertySourceLoader().load("production",
			new org.springframework.core.io.FileSystemResource("src/main/resources/application.yaml"))
			.forEach(environment.getPropertySources()::addLast);
		var bound = org.springframework.boot.context.properties.bind.Binder.get(environment)
			.bind("app.reading-credits", ReadingCreditProperties.class).get();
		org.assertj.core.api.Assertions.assertThat(bound.costs().tarot()).isEqualTo(new ReadingCreditProperties.Tarot(3, 3, 4));
		org.assertj.core.api.Assertions.assertThat(bound.costs().saju()).isEqualTo(4);
		org.assertj.core.api.Assertions.assertThat(bound.costs().sajuAnnual()).isEqualTo(6);
	}

	@Test
	void pricesAnnualReportIndependentlyFromGeneralSajuAndKeepsTarotPricesConfigurable() {
		var properties = new ReadingCreditProperties(10,
			new ReadingCreditProperties.Costs(new ReadingCreditProperties.Tarot(3, 3, 4), 4, 6),
			Duration.ofMinutes(5), "0 */5 * * * *", 1000, 50);
		var tarot = com.myeongro.api.domain.reading.entity.ReadingKind.TAROT;
		var saju = com.myeongro.api.domain.reading.entity.ReadingKind.SAJU;
		org.assertj.core.api.Assertions.assertThat(properties.cost(tarot, "mind_three_card", null)).isEqualTo(3);
		org.assertj.core.api.Assertions.assertThat(properties.cost(tarot, "relationship_three_card", null)).isEqualTo(3);
		org.assertj.core.api.Assertions.assertThat(properties.cost(tarot, "choice_five_card", null)).isEqualTo(4);
		org.assertj.core.api.Assertions.assertThat(properties.cost(saju, null, null)).isEqualTo(4);
		org.assertj.core.api.Assertions.assertThat(properties.cost(saju, null, "birth_annual_question")).isEqualTo(4);
		org.assertj.core.api.Assertions.assertThat(properties.cost(saju, null, "annual_monthly")).isEqualTo(6);
		assertThatThrownBy(() -> properties.cost(saju, null, "unknown")).isInstanceOf(IllegalArgumentException.class);
	}

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
				new ReadingCreditProperties.Tarot(0, 0, 0), 0, 0
			),
			Duration.ofMinutes(5),
			"0 */5 * * * *",
			-1,
			101
		);
		try (var factory = Validation.buildDefaultValidatorFactory()) {
			var paths = factory.getValidator().validate(invalid).stream()
				.map(violation -> violation.getPropertyPath().toString())
				.toList();
			org.assertj.core.api.Assertions.assertThat(paths)
				.contains(
					"dailyFreeGrant",
					"costs.saju",
					"costs.sajuAnnual",
					"costs.tarot.mindThreeCard",
					"costs.tarot.relationshipThreeCard",
					"costs.tarot.choiceFiveCard",
					"globalDailyAttemptLimit",
					"usageAlertPercent"
				);
		}
	}

	private ReadingCreditProperties properties(Duration staleAfter, String cleanupCron) {
		return new ReadingCreditProperties(
			10,
			new ReadingCreditProperties.Costs(
				new ReadingCreditProperties.Tarot(2, 2, 3), 4, 6
			),
			staleAfter,
			cleanupCron,
			1000,
			50
		);
	}
}
