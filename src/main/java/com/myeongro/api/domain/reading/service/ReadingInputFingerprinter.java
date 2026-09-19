package com.myeongro.api.domain.reading.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.myeongro.api.domain.reading.entity.ReadingKind;

@Component
public class ReadingInputFingerprinter {

	private static final String HMAC_ALGORITHM = "HmacSHA256";
	private static final int MINIMUM_SECRET_BYTES = 32;

	private final ObjectMapper objectMapper;
	private final byte[] sajuSecret;

	public ReadingInputFingerprinter(
		ObjectMapper objectMapper,
		@Value("${app.reading.saju-idempotency-secret}") String sajuSecret
	) {
		if (sajuSecret == null
			|| sajuSecret.getBytes(StandardCharsets.UTF_8).length < MINIMUM_SECRET_BYTES) {
			throw new IllegalArgumentException(
				"app.reading.saju-idempotency-secret must be at least 32 bytes"
			);
		}
		this.objectMapper = objectMapper;
		this.sajuSecret = sajuSecret.getBytes(StandardCharsets.UTF_8).clone();
	}

	public String fingerprint(ReadingRequestInput input) {
		Map<String, Object> material = ReadingInputSupport.hashMaterial(
			input.kind(), input.spreadType(), input.schemaVersion(), input.idempotencyPayload()
		);
		byte[] canonical = canonicalBytes(material);
		byte[] digest = input.kind() == ReadingKind.SAJU
			? hmac(canonical)
			: sha256(canonical);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
	}

	private byte[] canonicalBytes(Map<String, Object> input) {
		try {
			return objectMapper.writer()
				.with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
				.writeValueAsBytes(input);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Cannot serialize reading input", exception);
		}
	}

	private byte[] hmac(byte[] input) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(sajuSecret, HMAC_ALGORITHM));
			return mac.doFinal(input);
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("Cannot fingerprint saju input", exception);
		}
	}

	private byte[] sha256(byte[] input) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(input);
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("Cannot fingerprint reading input", exception);
		}
	}
}
