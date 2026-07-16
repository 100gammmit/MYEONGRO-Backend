package com.myeongro.api.domain.tarotdraw.repository;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface TarotDrawSessionRepository {

	boolean create(TarotDrawSessionState state, Duration ttl);

	Optional<TarotDrawSessionState> findActive(UUID userId);

	Optional<TarotDrawSessionState> findOwned(UUID userId, String sessionId);

	boolean select(TarotDrawSessionState expected, TarotDrawSessionState updated);

	boolean abandon(UUID userId, String sessionId);

	ConsumeResult consume(UUID userId, String sessionId, UUID requestId, String inputHash);

	boolean finalizeConsumption(UUID userId, String sessionId, UUID requestId, String inputHash);

	enum ConsumeResult {
		CONSUMED,
		IDEMPOTENT,
		NOT_FOUND,
		STATE_CONFLICT,
		ALREADY_CONSUMED
	}
}
