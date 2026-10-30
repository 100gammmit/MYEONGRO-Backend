package com.myeongro.api.global.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

class IpRateLimiterTests {

	private static final String IP_SECRET = "test-only-rate-limit-ip-secret-32-bytes";
	private static final String SELECTION_SECRET = "test-only-tarot-selection-secret-32-bytes";
	private static final String TAROT_SECRET = "test-only-tarot-idempotency-secret-32-bytes";
	private static final String SAJU_SECRET = "test-only-saju-idempotency-secret-32-bytes";

	private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
	private final IpRateLimiter limiter = limiter(IP_SECRET);

	private IpRateLimiter limiter(String ipSecret) {
		return new IpRateLimiter(
			redisTemplate,
			new IpRateLimitProperties(20, 20, 5),
			ipSecret,
			SELECTION_SECRET,
			TAROT_SECRET,
			SAJU_SECRET
		);
	}

	private void countIs(Long count) {
		when(redisTemplate.execute(any(), anyList(), any())).thenReturn(count);
	}

	@SuppressWarnings("unchecked")
	private String keyFor(IpRateLimiter.Scope scope, String address) {
		countIs(1L);
		limiter.tryAcquire(scope, address);
		ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
		verify(redisTemplate, org.mockito.Mockito.atLeastOnce()).execute(any(), keys.capture(), any());
		return keys.getValue().getFirst();
	}

	@Test
	void allowsUpToTheLimitAndRejectsTheNextRequest() {
		countIs(20L);
		assertThat(limiter.tryAcquire(IpRateLimiter.Scope.OAUTH_START, "203.0.113.7")).isTrue();

		countIs(21L);
		assertThat(limiter.tryAcquire(IpRateLimiter.Scope.OAUTH_START, "203.0.113.7")).isFalse();
	}

	@Test
	void appliesEachScopesOwnLimit() {
		countIs(6L);

		assertThat(limiter.tryAcquire(IpRateLimiter.Scope.SIGNUP, "203.0.113.7")).isFalse();
		assertThat(limiter.tryAcquire(IpRateLimiter.Scope.OAUTH_CALLBACK, "203.0.113.7")).isTrue();
	}

	@Test
	void letsRequestsThroughWhenRedisIsUnavailable() {
		when(redisTemplate.execute(any(), anyList(), any()))
			.thenThrow(new RedisConnectionFailureException("redis down"));

		assertThat(limiter.tryAcquire(IpRateLimiter.Scope.OAUTH_START, "203.0.113.7")).isTrue();
	}

	@Test
	void keepsTheAddressOutOfTheRedisKey() {
		String key = keyFor(IpRateLimiter.Scope.OAUTH_START, "203.0.113.7");

		assertThat(key).startsWith("myeongro:ratelimit:oauth-start:").doesNotContain("203.0.113.7");
	}

	@Test
	void separatesScopesForTheSameAddress() {
		assertThat(keyFor(IpRateLimiter.Scope.OAUTH_START, "203.0.113.7"))
			.isNotEqualTo(keyFor(IpRateLimiter.Scope.OAUTH_CALLBACK, "203.0.113.7"));
	}

	@Test
	void countsAnIpv6SubscriberAsOneNetwork() {
		assertThat(IpRateLimiter.network("2001:db8:1:2:aaaa::1"))
			.isEqualTo(IpRateLimiter.network("2001:db8:1:2:bbbb::9"))
			.isEqualTo(IpRateLimiter.network("[2001:db8:1:2::5]"))
			.isNotEqualTo(IpRateLimiter.network("2001:db8:1:3::1"));
	}

	@Test
	void treatsAnIpv4MappedAddressAsItsIpv4Address() {
		assertThat(IpRateLimiter.network("::ffff:203.0.113.7")).isEqualTo("203.0.113.7");
	}

	@Test
	void rejectsAShortSecret() {
		assertThatThrownBy(() -> limiter("too-short"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("at least 32 bytes");
	}

	@Test
	void rejectsASecretReusedFromReadings() {
		assertThatThrownBy(() -> limiter(SAJU_SECRET))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("distinct");
	}
}
