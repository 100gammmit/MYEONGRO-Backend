package com.myeongro.api.domain.consent.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProductionConsentConfigurationValidatorTests {

	@Test
	void acceptsReleasedDocumentVersions() {
		var validator = new ProductionConsentConfigurationValidator(
			"2026-09-20",
			"2026-09-20"
		);

		assertThatCode(validator::validate).doesNotThrowAnyException();
	}

	@Test
	void rejectsDraftDocumentVersionsInProduction() {
		var validator = new ProductionConsentConfigurationValidator(
			"draft-2026-09-07",
			"2026-09-20"
		);

		assertThatThrownBy(validator::validate)
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("ai-overseas-transfer");
	}
}
