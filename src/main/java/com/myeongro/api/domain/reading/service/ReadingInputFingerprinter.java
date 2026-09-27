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

@Component
public class ReadingInputFingerprinter {

	private static final String HMAC_ALGORITHM = "HmacSHA256";
	private static final int MINIMUM_SECRET_BYTES = 32;

	private final ObjectMapper objectMapper;
	private final byte[] tarotSecret;
	private final byte[] sajuSecret;

	/**
	 * Each reading kind signs its idempotency fingerprint with a dedicated key.
	 * The tarot selection key only ranks cards and is accepted here solely to
	 * reject a configuration that reuses it for fingerprints.
	 */
	public ReadingInputFingerprinter(
		ObjectMapper objectMapper,
		@Value("${app.reading.tarot-idempotency-secret}") String tarotSecret,
		@Value("${app.reading.saju-idempotency-secret}") String sajuSecret,
		@Value("${app.reading.tarot-selection-secret}") String tarotSelectionSecret
	) {
		byte[] tarot = requireSecret(tarotSecret, "app.reading.tarot-idempotency-secret");
		byte[] saju = requireSecret(sajuSecret, "app.reading.saju-idempotency-secret");
		byte[] selection = bytes(tarotSelectionSecret);
		if (MessageDigest.isEqual(tarot, saju)
			|| MessageDigest.isEqual(tarot, selection)
			|| MessageDigest.isEqual(saju, selection)) {
			throw new IllegalArgumentException(
				"Tarot idempotency, Saju idempotency and tarot selection secrets must be distinct"
			);
		}
		this.objectMapper = objectMapper;
		this.tarotSecret = tarot;
		this.sajuSecret = saju;
	}

	public String fingerprint(ReadingRequestInput input) {
		Map<String, Object> material = ReadingInputSupport.hashMaterial(
			input.kind(), input.spreadType(), input.schemaVersion(), input.idempotencyPayload()
		);
		byte[] secret = switch (input.kind()) {
			case TAROT -> tarotSecret;
			case SAJU -> sajuSecret;
		};
		byte[] digest = hmac(secret, canonicalBytes(material));
		return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
	}

	private static byte[] requireSecret(String secret, String propertyName) {
		if (secret == null || bytes(secret).length < MINIMUM_SECRET_BYTES) {
			throw new IllegalArgumentException(propertyName + " must be at least 32 bytes");
		}
		return bytes(secret);
	}

	private static byte[] bytes(String value) {
		return value.getBytes(StandardCharsets.UTF_8);
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

	private byte[] hmac(byte[] secret, byte[] input) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
			return mac.doFinal(input);
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("Cannot fingerprint reading input", exception);
		}
	}
}
