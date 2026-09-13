package com.myeongro.api.global.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.myeongro.api.global.auth.oauth.SignupAttemptCoordinator.CompletionClaim;

class RedisSignupAttemptCoordinatorTests {

	private final StringRedisTemplate redisTemplate =
		org.mockito.Mockito.mock(StringRedisTemplate.class);
	@SuppressWarnings("unchecked")
	private final ValueOperations<String, String> values =
		org.mockito.Mockito.mock(ValueOperations.class);
	private final RedisSignupAttemptCoordinator coordinator =
		new RedisSignupAttemptCoordinator(redisTemplate, Duration.ofMinutes(30));

	@Test
	void createsAPendingAttemptWithTheSessionTtl() {
		when(redisTemplate.opsForValue()).thenReturn(values);
		when(values.setIfAbsent(anyString(), eq("pending"), eq(Duration.ofMinutes(30))))
			.thenReturn(true);

		String attemptId = coordinator.beginAttempt();

		assertThat(attemptId).isNotBlank();
		verify(values).setIfAbsent(
			eq(RedisSignupAttemptCoordinator.KEY_PREFIX + attemptId),
			eq("pending"),
			eq(Duration.ofMinutes(30))
		);
	}

	@Test
	void mapsTheAtomicCompletionClaimOutcomes() {
		when(redisTemplate.execute(any(), anyList(), any(), any(), any()))
			.thenReturn(1L, 2L, 0L);

		assertThat(coordinator.claimCompletion("attempt-1"))
			.isEqualTo(CompletionClaim.ACQUIRED);
		assertThat(coordinator.claimCompletion("attempt-1"))
			.isEqualTo(CompletionClaim.ALREADY_COMPLETED);
		assertThat(coordinator.claimCompletion("attempt-1"))
			.isEqualTo(CompletionClaim.REJECTED);
	}
}
