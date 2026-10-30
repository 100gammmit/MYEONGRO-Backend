package com.myeongro.api.global.ratelimit;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Fixed-window request counter keyed by an HMAC of the client network, so Redis never holds an IP.
 * Limits protect session and OAuth capacity, not user data, so a Redis failure lets requests through.
 */
@Component
public class IpRateLimiter {

	private static final Logger log = LoggerFactory.getLogger(IpRateLimiter.class);
	static final String KEY_PREFIX = "myeongro:ratelimit:";
	private static final String HMAC_ALGORITHM = "HmacSHA256";
	private static final int MINIMUM_SECRET_BYTES = 32;
	private static final int IPV6_NETWORK_BYTES = 8;
	private static final DefaultRedisScript<Long> COUNT_SCRIPT = new DefaultRedisScript<>(
		"""
		local count = redis.call('incr', KEYS[1])
		if count == 1 then
		  redis.call('pexpire', KEYS[1], ARGV[1])
		end
		return count
		""",
		Long.class
	);

	public enum Scope {
		OAUTH_START("oauth-start", Duration.ofMinutes(1)),
		OAUTH_CALLBACK("oauth-callback", Duration.ofMinutes(1)),
		SIGNUP("signup", Duration.ofDays(1));

		private final String keyName;
		private final Duration window;

		Scope(String keyName, Duration window) {
			this.keyName = keyName;
			this.window = window;
		}
	}

	private final StringRedisTemplate redisTemplate;
	private final IpRateLimitProperties properties;
	private final byte[] secret;

	/**
	 * The reading keys are accepted solely to reject a configuration that reuses one for IP hashing.
	 */
	public IpRateLimiter(
		StringRedisTemplate redisTemplate,
		IpRateLimitProperties properties,
		@Value("${app.rate-limit.ip-secret}") String ipSecret,
		@Value("${app.reading.tarot-selection-secret}") String tarotSelectionSecret,
		@Value("${app.reading.tarot-idempotency-secret}") String tarotIdempotencySecret,
		@Value("${app.reading.saju-idempotency-secret}") String sajuIdempotencySecret
	) {
		byte[] ip = bytes(ipSecret);
		if (ip.length < MINIMUM_SECRET_BYTES) {
			throw new IllegalArgumentException("app.rate-limit.ip-secret must be at least 32 bytes");
		}
		if (MessageDigest.isEqual(ip, bytes(tarotSelectionSecret))
			|| MessageDigest.isEqual(ip, bytes(tarotIdempotencySecret))
			|| MessageDigest.isEqual(ip, bytes(sajuIdempotencySecret))) {
			throw new IllegalArgumentException("app.rate-limit.ip-secret must be distinct from reading secrets");
		}
		this.redisTemplate = redisTemplate;
		this.properties = properties;
		this.secret = ip;
	}

	public boolean tryAcquire(Scope scope, String remoteAddress) {
		int limit = properties.limit(scope);
		Long count;
		try {
			count = redisTemplate.execute(
				COUNT_SCRIPT,
				List.of(KEY_PREFIX + scope.keyName + ":" + hash(network(remoteAddress))),
				Long.toString(scope.window.toMillis())
			);
		} catch (RuntimeException exception) {
			log.warn("IP_RATE_LIMIT_UNAVAILABLE scope={}", scope);
			return true;
		}
		if (count == null) {
			return true;
		}
		// One line per window keeps a flood from turning into a logging bill.
		if (count == limit + 1L) {
			log.warn("IP_RATE_LIMITED scope={}", scope);
		}
		return count <= limit;
	}

	// One IPv6 subscriber owns a whole /64, so counting single addresses would not limit anyone.
	static String network(String remoteAddress) {
		if (!remoteAddress.contains(":")) {
			return remoteAddress;
		}
		try {
			// A literal containing a colon is parsed without a DNS lookup.
			InetAddress address = InetAddress.getByName(remoteAddress);
			if (address instanceof Inet6Address) {
				byte[] network = Arrays.copyOf(address.getAddress(), IPV6_NETWORK_BYTES);
				return HexFormat.of().formatHex(network) + "/64";
			}
			return address.getHostAddress();
		} catch (UnknownHostException exception) {
			return remoteAddress;
		}
	}

	private String hash(String network) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(bytes(network)));
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
		}
	}

	private static byte[] bytes(String value) {
		return value.getBytes(StandardCharsets.UTF_8);
	}
}
