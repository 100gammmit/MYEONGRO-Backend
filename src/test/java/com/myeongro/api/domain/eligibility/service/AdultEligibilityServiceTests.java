package com.myeongro.api.domain.eligibility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.myeongro.api.domain.eligibility.repository.AdultEligibilityRepository;

class AdultEligibilityServiceTests {

	private static final UUID USER_ID =
		UUID.fromString("43bc72f9-eed1-4e4b-8717-6fe969b4ea43");
	private static final Instant NOW = Instant.parse("2026-09-09T00:00:00Z");
	private static final String VERSION = "2026-09-09";

	private final AdultEligibilityRepository repository =
		org.mockito.Mockito.mock(AdultEligibilityRepository.class);
	private final AdultEligibilityService service = new AdultEligibilityService(
		repository,
		VERSION,
		Clock.fixed(NOW, ZoneOffset.UTC)
	);

	@Test
	void recordsTheCurrentPolicyVersionAfterOAuth() {
		service.confirmCurrentForUser(USER_ID, VERSION);

		verify(repository).saveConfirmation(
			USER_ID,
			VERSION,
			NOW,
			"pre-oauth-self-declaration"
		);
	}

	@Test
	void rejectsAStaleOrForgedConfirmationVersion() {
		assertThatThrownBy(() -> service.confirmCurrentForUser(USER_ID, "2026-01-01"))
			.isInstanceOf(AdultEligibilityVersionMismatchException.class);
	}

	@Test
	void exposesOnlyAnExactCurrentVersionAsValid() {
		assertThat(service.isCurrentVersion(VERSION)).isTrue();
		assertThat(service.isCurrentVersion("2026-01-01")).isFalse();
		assertThat(service.isCurrentVersion(null)).isFalse();
	}
}
