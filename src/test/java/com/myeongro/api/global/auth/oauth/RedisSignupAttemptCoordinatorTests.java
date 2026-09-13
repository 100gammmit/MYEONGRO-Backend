package com.myeongro.api.global.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.myeongro.api.global.auth.oauth.SignupAttemptCoordinator.AttemptState;
import com.myeongro.api.global.auth.oauth.SignupAttemptCoordinator.CompletionClaim;

class RedisSignupAttemptCoordinatorTests {

	private final StringRedisTemplate redisTemplate =
		org.mockito.Mockito.mock(StringRedisTemplate.class);
	private final RedisSignupAttemptCoordinator coordinator =
		new RedisSignupAttemptCoordinator(redisTemplate, Duration.ofMinutes(30));

	@Test
	void createsAnIdentityScopedAttemptGeneration() {
		when(redisTemplate.execute(any(), anyList(), any(), any(), any()))
			.thenReturn("generation-1", "generation-1");

		SignupAttemptCoordinator.Attempt first = coordinator.beginAttempt("kakao", "12345");
		SignupAttemptCoordinator.Attempt second = coordinator.beginAttempt("kakao", "12345");

		assertThat(first.attemptId()).isNotBlank().isNotEqualTo(second.attemptId());
		assertThat(first.generationId()).isEqualTo("generation-1");
		assertThat(second.generationId()).isEqualTo("generation-1");
	}

	@Test
	void mapsTheAtomicCompletionClaimOutcomes() {
		when(redisTemplate.execute(any(), anyList(), any(), any(), any(), any()))
			.thenReturn(1L, 2L, 0L);

		PendingSignupSessionPrincipal principal = pending("attempt-1", "generation-1");

		assertThat(coordinator.claimCompletion(principal))
			.isEqualTo(CompletionClaim.ACQUIRED);
		assertThat(coordinator.claimCompletion(principal))
			.isEqualTo(CompletionClaim.ALREADY_COMPLETED);
		assertThat(coordinator.claimCompletion(principal))
			.isEqualTo(CompletionClaim.REJECTED);
	}

	@Test
	void readsAndRefreshesEveryKnownStateAndReportsAMissingAttempt() {
		when(redisTemplate.execute(any(), anyList(), any()))
			.thenReturn(
				"generation-1:pending",
				"generation-1:completing:attempt-1",
				"generation-1:completed",
				"generation-1:cancelling:attempt-1",
				"generation-1:cancelled",
				"generation-2:completed",
				null
			);
		PendingSignupSessionPrincipal principal = pending("attempt-1", "generation-1");

		assertThat(coordinator.state(principal)).isEqualTo(AttemptState.PENDING);
		assertThat(coordinator.state(principal)).isEqualTo(AttemptState.COMPLETING);
		assertThat(coordinator.state(principal)).isEqualTo(AttemptState.COMPLETED);
		assertThat(coordinator.state(principal)).isEqualTo(AttemptState.CANCELLING);
		assertThat(coordinator.state(principal)).isEqualTo(AttemptState.CANCELLED);
		assertThat(coordinator.state(principal)).isEqualTo(AttemptState.STALE);
		assertThat(coordinator.state(principal)).isEqualTo(AttemptState.MISSING);
	}

	private PendingSignupSessionPrincipal pending(String attemptId, String generationId) {
		return new PendingSignupSessionPrincipal(
			attemptId,
			generationId,
			"kakao",
			"12345",
			"명로 사용자",
			"user@example.com",
			"access-token"
		);
	}
}
