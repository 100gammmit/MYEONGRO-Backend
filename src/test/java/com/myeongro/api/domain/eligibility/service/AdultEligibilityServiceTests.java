package com.myeongro.api.domain.eligibility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
	void recordsTheCurrentPolicyVersionWhenSignupCompletes() {
		service.confirmSignupForUser(USER_ID, "generation-1");

		verify(repository).saveConfirmation(
			USER_ID,
			VERSION,
			NOW,
			"oauth-signup-self-declaration",
			"generation-1"
		);
	}

	@Test
	void matchesAConfirmationOnlyToItsSignupGeneration() {
		when(repository.hasSignupConfirmation(USER_ID, "generation-1")).thenReturn(true);

		assertThat(service.hasSignupConfirmation(USER_ID, "generation-1")).isTrue();
		verify(repository).hasSignupConfirmation(USER_ID, "generation-1");
	}

	@Test
	void recognizesAnyRecordedSignupConfirmationWithoutReaskingByVersion() {
		when(repository.hasConfirmation(USER_ID)).thenReturn(true);

		assertThat(service.hasConfirmationForUser(USER_ID)).isTrue();
		verify(repository).hasConfirmation(USER_ID);
	}
}
