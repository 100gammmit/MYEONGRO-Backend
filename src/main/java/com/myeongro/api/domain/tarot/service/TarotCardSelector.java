package com.myeongro.api.domain.tarot.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.myeongro.api.domain.tarot.model.MajorArcana;
import com.myeongro.api.domain.tarot.model.TarotSpreadType;

@Component
public class TarotCardSelector {

	private static final int CANDIDATE_COUNT = 5;
	private static final int MINIMUM_SECRET_BYTES = 32;
	private static final String HMAC_ALGORITHM = "HmacSHA256";

	private final byte[] secret;

	public TarotCardSelector(
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

	public List<String> select(
		UUID userId,
		UUID requestId,
		TarotSpreadType spread,
		List<Integer> selectedSlots
	) {
		requireSelection(userId, requestId, spread, selectedSlots);

		List<String> remaining = new ArrayList<>(MajorArcana.all());
		List<String> selected = new ArrayList<>(spread.cardCount());
		for (int positionIndex = 0; positionIndex < spread.cardCount(); positionIndex++) {
			int currentPosition = positionIndex;
			remaining.sort(Comparator.comparing(
				cardId -> digest(userId, requestId, spread, currentPosition, cardId),
				TarotCardSelector::compareUnsigned
			));
			String cardId = remaining.get(selectedSlots.get(positionIndex) - 1);
			selected.add(cardId);
			remaining.remove(cardId);
		}
		return List.copyOf(selected);
	}

	private void requireSelection(
		UUID userId,
		UUID requestId,
		TarotSpreadType spread,
		List<Integer> selectedSlots
	) {
		if (userId == null || requestId == null || spread == null) {
			throw new IllegalArgumentException("Tarot selection context is required");
		}
		if (selectedSlots == null || selectedSlots.size() != spread.cardCount()) {
			throw new IllegalArgumentException(
				spread.cardCount() + " selected slots are required for " + spread.value()
			);
		}
		if (selectedSlots.stream().anyMatch(
			slot -> slot == null || slot < 1 || slot > CANDIDATE_COUNT
		)) {
			throw new IllegalArgumentException("Each selected slot must be between 1 and 5");
		}
	}

	private byte[] digest(
		UUID userId,
		UUID requestId,
		TarotSpreadType spread,
		int positionIndex,
		String cardId
	) {
		String context = String.join(":",
			"tarot-selection-v1",
			userId.toString(),
			requestId.toString(),
			spread.value(),
			Integer.toString(positionIndex),
			cardId
		);
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
			return mac.doFinal(context.getBytes(StandardCharsets.UTF_8));
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("Cannot select tarot cards", exception);
		}
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
