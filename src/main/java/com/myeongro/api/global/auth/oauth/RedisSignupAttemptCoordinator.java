package com.myeongro.api.global.auth.oauth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisSignupAttemptCoordinator implements SignupAttemptCoordinator {

	static final String KEY_PREFIX = "myeongro:signup-identity:";
	private static final String PENDING = "pending";
	private static final String COMPLETING = "completing";
	private static final String COMPLETED = "completed";
	private static final String CANCELLING = "cancelling";
	private static final String CANCELLED = "cancelled";
	private static final DefaultRedisScript<String> BEGIN_SCRIPT = new DefaultRedisScript<>(
		"""
		local current = redis.call('get', KEYS[1])
		if current then
		  local generation, state = string.match(current, '^([^:]+):([^:]+)')
		  if state ~= ARGV[1] then
		    redis.call('pexpire', KEYS[1], ARGV[3])
		    return generation
		  end
		end
		redis.call('set', KEYS[1], ARGV[2] .. ':pending', 'PX', ARGV[3])
		return ARGV[2]
		""",
		String.class
	);
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
	public Attempt beginAttempt(String provider, String providerUserId) {
		String attemptId = UUID.randomUUID().toString();
		String candidateGenerationId = UUID.randomUUID().toString();
		String generationId = redisTemplate.execute(
			BEGIN_SCRIPT,
			List.of(key(provider, providerUserId)),
			CANCELLED,
			candidateGenerationId,
			Long.toString(attemptTtl.toMillis())
		);
		if (generationId == null || generationId.isBlank()) {
			throw new IllegalStateException("Unable to allocate a signup attempt");
		}
		return new Attempt(attemptId, generationId);
	}

	@Override
	public AttemptState state(PendingSignupSessionPrincipal principal) {
		String state = redisTemplate.execute(
			READ_AND_REFRESH_SCRIPT,
			List.of(key(principal.provider(), principal.providerUserId())),
			Long.toString(attemptTtl.toMillis())
		);
		if (state == null) return AttemptState.MISSING;
		String generationPrefix = principal.attemptGenerationId() + ":";
		if (!state.startsWith(generationPrefix)) return AttemptState.STALE;
		String lifecycle = state.substring(generationPrefix.length()).split(":", 2)[0];
		return switch (lifecycle) {
			case PENDING -> AttemptState.PENDING;
			case COMPLETING -> AttemptState.COMPLETING;
			case COMPLETED -> AttemptState.COMPLETED;
			case CANCELLING -> AttemptState.CANCELLING;
			case CANCELLED -> AttemptState.CANCELLED;
			default -> AttemptState.MISSING;
		};
	}

	@Override
	public CompletionClaim claimCompletion(PendingSignupSessionPrincipal principal) {
		Long result = redisTemplate.execute(
			CLAIM_COMPLETION_SCRIPT,
			List.of(key(principal.provider(), principal.providerUserId())),
			value(principal, PENDING),
			ownedValue(principal, COMPLETING),
			value(principal, COMPLETED),
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
	public boolean claimCancellation(PendingSignupSessionPrincipal principal) {
		return transition(
			principal,
			value(principal, PENDING),
			ownedValue(principal, CANCELLING)
		);
	}

	@Override
	public void markCompleted(PendingSignupSessionPrincipal principal) {
		transition(
			principal,
			ownedValue(principal, COMPLETING),
			value(principal, COMPLETED)
		);
	}

	@Override
	public void releaseCompletion(PendingSignupSessionPrincipal principal) {
		transition(
			principal,
			ownedValue(principal, COMPLETING),
			value(principal, PENDING)
		);
	}

	@Override
	public void markCancelled(PendingSignupSessionPrincipal principal) {
		transition(
			principal,
			ownedValue(principal, CANCELLING),
			value(principal, CANCELLED)
		);
	}

	private boolean transition(
		PendingSignupSessionPrincipal principal,
		String expected,
		String next
	) {
		Long result = redisTemplate.execute(
			TRANSITION_SCRIPT,
			List.of(key(principal.provider(), principal.providerUserId())),
			expected,
			next,
			Long.toString(attemptTtl.toMillis())
		);
		return Long.valueOf(1L).equals(result);
	}

	private String value(PendingSignupSessionPrincipal principal, String state) {
		return principal.attemptGenerationId() + ":" + state;
	}

	private String ownedValue(PendingSignupSessionPrincipal principal, String state) {
		return value(principal, state) + ":" + principal.attemptId();
	}

	private String key(String provider, String providerUserId) {
		return KEY_PREFIX + sha256(provider + "\u0000" + providerUserId);
	}

	private String sha256(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private static DefaultRedisScript<Long> script(String source) {
		return new DefaultRedisScript<>(source, Long.class);
	}
}
