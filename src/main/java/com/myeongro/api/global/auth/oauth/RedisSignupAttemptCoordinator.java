package com.myeongro.api.global.auth.oauth;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisSignupAttemptCoordinator implements SignupAttemptCoordinator {

	static final String KEY_PREFIX = "myeongro:signup-attempt:";
	private static final String PENDING = "pending";
	private static final String COMPLETING = "completing";
	private static final String COMPLETED = "completed";
	private static final String CANCELLING = "cancelling";
	private static final String CANCELLED = "cancelled";
	private static final DefaultRedisScript<Long> CLAIM_COMPLETION_SCRIPT = script(
		"""
		local current = redis.call('get', KEYS[1])
		if current == ARGV[1] then
		  redis.call('set', KEYS[1], ARGV[2], 'PX', ARGV[4])
		  return 1
		end
		if current == ARGV[3] then
		  return 2
		end
		return 0
		"""
	);
	private static final DefaultRedisScript<Long> TRANSITION_SCRIPT = script(
		"""
		local current = redis.call('get', KEYS[1])
		if current == ARGV[1] then
		  redis.call('set', KEYS[1], ARGV[2], 'PX', ARGV[3])
		  return 1
		end
		return 0
		"""
	);
	private static final DefaultRedisScript<String> READ_AND_REFRESH_SCRIPT = new DefaultRedisScript<>(
		"""
		local current = redis.call('get', KEYS[1])
		if current then
		  redis.call('pexpire', KEYS[1], ARGV[1])
		end
		return current
		""",
		String.class
	);

	private final StringRedisTemplate redisTemplate;
	private final Duration attemptTtl;

	public RedisSignupAttemptCoordinator(
		StringRedisTemplate redisTemplate,
		@Value("${server.servlet.session.timeout:30m}") Duration attemptTtl
	) {
		this.redisTemplate = redisTemplate;
		this.attemptTtl = attemptTtl;
	}

	@Override
	public String beginAttempt() {
		for (int attempt = 0; attempt < 3; attempt++) {
			String attemptId = UUID.randomUUID().toString();
			Boolean created = redisTemplate.opsForValue().setIfAbsent(
				key(attemptId),
				PENDING,
				attemptTtl
			);
			if (Boolean.TRUE.equals(created)) {
				return attemptId;
			}
		}
		throw new IllegalStateException("Unable to allocate a signup attempt");
	}

	@Override
	public AttemptState state(String attemptId) {
		String state = redisTemplate.execute(
			READ_AND_REFRESH_SCRIPT,
			List.of(key(attemptId)),
			Long.toString(attemptTtl.toMillis())
		);
		if (state == null) return AttemptState.MISSING;
		return switch (state) {
			case PENDING -> AttemptState.PENDING;
			case COMPLETING -> AttemptState.COMPLETING;
			case COMPLETED -> AttemptState.COMPLETED;
			case CANCELLING -> AttemptState.CANCELLING;
			case CANCELLED -> AttemptState.CANCELLED;
			default -> AttemptState.MISSING;
		};
	}

	@Override
	public CompletionClaim claimCompletion(String attemptId) {
		Long result = redisTemplate.execute(
			CLAIM_COMPLETION_SCRIPT,
			List.of(key(attemptId)),
			PENDING,
			COMPLETING,
			COMPLETED,
			Long.toString(attemptTtl.toMillis())
		);
		if (Long.valueOf(1L).equals(result)) {
			return CompletionClaim.ACQUIRED;
		}
		if (Long.valueOf(2L).equals(result)) {
			return CompletionClaim.ALREADY_COMPLETED;
		}
		return CompletionClaim.REJECTED;
	}

	@Override
	public boolean claimCancellation(String attemptId) {
		return transition(attemptId, PENDING, CANCELLING);
	}

	@Override
	public void markCompleted(String attemptId) {
		transition(attemptId, COMPLETING, COMPLETED);
	}

	@Override
	public void releaseCompletion(String attemptId) {
		transition(attemptId, COMPLETING, PENDING);
	}

	@Override
	public void markCancelled(String attemptId) {
		transition(attemptId, CANCELLING, CANCELLED);
	}

	private boolean transition(String attemptId, String expected, String next) {
		Long result = redisTemplate.execute(
			TRANSITION_SCRIPT,
			List.of(key(attemptId)),
			expected,
			next,
			Long.toString(attemptTtl.toMillis())
		);
		return Long.valueOf(1L).equals(result);
	}

	private String key(String attemptId) {
		return KEY_PREFIX + attemptId;
	}

	private static DefaultRedisScript<Long> script(String source) {
		return new DefaultRedisScript<>(source, Long.class);
	}
}
