package com.myeongro.api.domain.tarotdraw.service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class SecureTarotDrawEntropy implements TarotDrawEntropy {

	private static final int RANDOM_BYTES = 32;
	private final SecureRandom secureRandom = new SecureRandom();

	@Override
	public String sessionId() {
		return "ds_" + randomValue();
	}

	@Override
	public String candidateToken() {
		return "ct_" + randomValue();
	}

	@Override
	public List<String> shuffledCopy(List<String> cards) {
		List<String> shuffled = new ArrayList<>(cards);
		Collections.shuffle(shuffled, secureRandom);
		return List.copyOf(shuffled);
	}

	private String randomValue() {
		byte[] bytes = new byte[RANDOM_BYTES];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
