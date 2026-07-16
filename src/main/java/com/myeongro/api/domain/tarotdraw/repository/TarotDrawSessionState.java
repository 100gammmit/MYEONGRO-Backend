package com.myeongro.api.domain.tarotdraw.repository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Collections;

import com.myeongro.api.domain.reading.entity.TarotSpreadType;

public record TarotDrawSessionState(
	String id,
	UUID userId,
	TarotSpreadType spreadType,
	String status,
	int positionIndex,
	long version,
	Instant createdAt,
	Instant expiresAt,
	List<String> remainingCards,
	List<String> selectedCards,
	Map<String, String> candidates,
	UUID consumedRequestId,
	String consumedInputHash
) {

	public TarotDrawSessionState {
		remainingCards = List.copyOf(remainingCards);
		selectedCards = List.copyOf(selectedCards);
		candidates = Collections.unmodifiableMap(new LinkedHashMap<>(candidates));
	}

	public boolean complete() {
		return "complete".equals(status);
	}

	public boolean consumed() {
		return "consumed".equals(status);
	}

	public TarotDrawSessionState consumedBy(UUID requestId, String inputHash) {
		return new TarotDrawSessionState(
			id, userId, spreadType, "consumed", positionIndex, version + 1,
			createdAt, expiresAt,
			remainingCards, selectedCards, Map.of(), requestId, inputHash
		);
	}
}
