package com.myeongro.api.domain.consent.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProductionConsentConfigurationValidatorTests {

	@Test
	void acceptsReleasedDocumentVersions() {
		var validator = new ProductionConsentConfigurationValidator(
			"2026-08-28",
			"2026-09-20",
			"2026-09-20",
			"2026-09-09"
		);

		assertThatCode(validator::validate).doesNotThrowAnyException();
	}

	@Test
	void rejectsDraftDocumentVersionsInProduction() {
		var validator = new ProductionConsentConfigurationValidator(
			"2026-08-28",
			"draft-2026-09-07",
			"2026-09-20",
			"2026-09-09"
		);

		assertThatThrownBy(validator::validate)
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("ai-overseas-transfer");
	}

	@Test
	void rejectsADraftTermsVersionInProduction() {
		var validator = new ProductionConsentConfigurationValidator(
			"draft-terms",
			"2026-09-20",
			"2026-09-20",
			"2026-09-09"
		);

		assertThatThrownBy(validator::validate)
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("terms");
	}

	@Test
	void rejectsADraftAdultEligibilityVersionInProduction() {
		var validator = new ProductionConsentConfigurationValidator(
			"2026-09-09",
			"2026-09-20",
			"2026-09-20",
			"draft-adult-policy"
		);

		assertThatThrownBy(validator::validate)
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("adult-eligibility");
	}
}
