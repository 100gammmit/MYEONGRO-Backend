package com.myeongro.api.domain.consent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
@Profile("prod")
public class ProductionConsentConfigurationValidator {

	private final String termsVersion;
	private final String overseasTransferVersion;
	private final String sajuInputVersion;
	private final String adultEligibilityVersion;

	public ProductionConsentConfigurationValidator(
		@Value("${app.consent.versions.terms}") String termsVersion,
		@Value("${app.consent.versions.ai-overseas-transfer}") String overseasTransferVersion,
		@Value("${app.consent.versions.saju-input}") String sajuInputVersion,
		@Value("${app.eligibility.adult-policy-version}") String adultEligibilityVersion
	) {
		this.termsVersion = termsVersion;
		this.overseasTransferVersion = overseasTransferVersion;
		this.sajuInputVersion = sajuInputVersion;
		this.adultEligibilityVersion = adultEligibilityVersion;
	}

	@PostConstruct
	void validate() {
		validateReleaseVersion("terms", termsVersion);
		validateReleaseVersion("ai-overseas-transfer", overseasTransferVersion);
		validateReleaseVersion("saju-input", sajuInputVersion);
		validateReleaseVersion("adult-eligibility", adultEligibilityVersion);
	}

	private void validateReleaseVersion(String documentType, String version) {
		if (version == null || version.isBlank() || version.startsWith("draft-")) {
			throw new IllegalStateException(
				"Production consent version is not released: " + documentType
			);
		}
	}
}
