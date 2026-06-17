package com.myeongro.api.global.guest;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * GuestSessionSigner
 * <p></p>
 * @author 100minha
 */
@Service
public class GuestSessionSigner {

	private static final int MIN_SECRET_BYTES = 32;
	private static final String HMAC_SHA_256 = "HmacSHA256";
	private static final DateTimeFormatter INSTANT_WITH_MILLISECONDS =
		new DateTimeFormatterBuilder().appendInstant(3).toFormatter();

	private final byte[] signingSecret;
	private final Clock clock;
	private final ObjectMapper objectMapper;

	@Autowired
	public GuestSessionSigner(
		@Value("${app.guest.signing-secret}") String signingSecret
	) {
		this(signingSecret, Clock.systemUTC());
	}

	GuestSessionSigner(String signingSecret, Clock clock) {
		if (signingSecret == null
			|| signingSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
			throw new IllegalArgumentException(
				"app.guest.signing-secret must be at least 32 bytes"
			);
		}
		this.signingSecret = signingSecret.getBytes(StandardCharsets.UTF_8);
		this.clock = clock;
		this.objectMapper = new ObjectMapper();
	}

	public String sign(UUID sessionId, Instant expiresAt) {
		String payload = "{\"sessionId\":\"%s\",\"expiresAt\":\"%s\"}".formatted(
			sessionId,
			INSTANT_WITH_MILLISECONDS.format(expiresAt)
		);
		String encodedPayload = encode(payload.getBytes(StandardCharsets.UTF_8));
		return encodedPayload + "." + encode(sign(encodedPayload));
	}

	public Optional<GuestSession> verify(String token) {
		if (token == null || token.isBlank()) {
			return Optional.empty();
		}

		String[] parts = token.split("\\.", -1);
		if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
			return Optional.empty();
		}

		try {
			byte[] expectedSignature = sign(parts[0]);
			byte[] actualSignature = Base64.getUrlDecoder().decode(parts[1]);
			if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
				return Optional.empty();
			}

			String payload = new String(
				Base64.getUrlDecoder().decode(parts[0]),
				StandardCharsets.UTF_8
			);
			JsonNode json = objectMapper.readTree(payload);
			UUID sessionId = UUID.fromString(json.required("sessionId").asText());
			Instant expiresAt = Instant.parse(json.required("expiresAt").asText());
			if (!expiresAt.isAfter(clock.instant())) {
				return Optional.empty();
			}
			return Optional.of(new GuestSession(sessionId, expiresAt));
		} catch (Exception exception) {
			return Optional.empty();
		}
	}

	private byte[] sign(String encodedPayload) {
		try {
			Mac mac = Mac.getInstance(HMAC_SHA_256);
			mac.init(new SecretKeySpec(signingSecret, HMAC_SHA_256));
			return mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8));
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("Cannot sign guest session", exception);
		}
	}

	private String encode(byte[] value) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
	}
}
