package com.myeongro.api.domain.tarot.selection;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TarotCardRanker {

	private static final int MINIMUM_SECRET_BYTES = 32;
	private static final String HMAC_ALGORITHM = "HmacSHA256";

	private final byte[] secret;

	public TarotCardRanker(
		@Value("${app.reading.tarot-selection-secret}") String secret
	) {
		if (secret == null
			|| secret.getBytes(StandardCharsets.UTF_8).length < MINIMUM_SECRET_BYTES) {
			throw new IllegalArgumentException(
				"app.reading.tarot-selection-secret must be at least 32 bytes"
			);
		}
		this.secret = secret.getBytes(StandardCharsets.UTF_8).clone();
	}

	public List<String> rank(
		String namespace,
		List<String> context,
		List<String> candidates
	) {
		if (namespace == null || namespace.isBlank()
			|| context == null || context.stream().anyMatch(this::isBlank)
			|| candidates == null || candidates.isEmpty()
			|| candidates.stream().anyMatch(this::isBlank)
			|| candidates.stream().distinct().count() != candidates.size()) {
			throw new IllegalArgumentException("Tarot ranking context and candidates are required");
		}
		List<String> ranked = new ArrayList<>(candidates);
		ranked.sort(Comparator.<String, byte[]>comparing(
			candidate -> digest(namespace, context, candidate),
			TarotCardRanker::compareUnsigned
		).thenComparing(Comparator.naturalOrder()));
		return List.copyOf(ranked);
	}

	private byte[] digest(String namespace, List<String> context, String candidate) {
		List<String> parts = new ArrayList<>(context.size() + 2);
		parts.add(namespace);
		parts.addAll(context);
		parts.add(candidate);
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
			return mac.doFinal(String.join(":", parts).getBytes(StandardCharsets.UTF_8));
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("Cannot rank tarot cards", exception);
		}
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private static int compareUnsigned(byte[] left, byte[] right) {
		for (int index = 0; index < Math.min(left.length, right.length); index++) {
			int comparison = Integer.compare(
				Byte.toUnsignedInt(left[index]),
				Byte.toUnsignedInt(right[index])
			);
			if (comparison != 0) {
				return comparison;
			}
		}
		return Integer.compare(left.length, right.length);
	}
}
