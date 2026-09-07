package com.myeongro.api.domain.consent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
@Profile("prod")
public class ProductionConsentConfigurationValidator {

	private final String overseasTransferVersion;
	private final String sajuInputVersion;

	public ProductionConsentConfigurationValidator(
		@Value("${app.consent.versions.ai-overseas-transfer}") String overseasTransferVersion,
		@Value("${app.consent.versions.saju-input}") String sajuInputVersion
	) {
		this.overseasTransferVersion = overseasTransferVersion;
		this.sajuInputVersion = sajuInputVersion;
	}

	@PostConstruct
	void validate() {
		validateReleaseVersion("ai-overseas-transfer", overseasTransferVersion);
		validateReleaseVersion("saju-input", sajuInputVersion);
	}

	private void validateReleaseVersion(String documentType, String version) {
		if (version == null || version.isBlank() || version.startsWith("draft-")) {
			throw new IllegalStateException(
				"Production consent version is not released: " + documentType
			);
		}
	}
}
