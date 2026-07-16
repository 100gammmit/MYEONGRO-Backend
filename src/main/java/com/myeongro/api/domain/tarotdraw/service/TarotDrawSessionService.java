package com.myeongro.api.domain.tarotdraw.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.myeongro.api.domain.reading.entity.MajorArcana;
import com.myeongro.api.domain.reading.entity.TarotSpreadType;
import com.myeongro.api.domain.tarotdraw.exception.TarotDrawSessionException;
import com.myeongro.api.domain.tarotdraw.repository.TarotDrawSessionRepository;
import com.myeongro.api.domain.tarotdraw.repository.TarotDrawSessionRepository.ConsumeResult;
import com.myeongro.api.domain.tarotdraw.repository.TarotDrawSessionState;

@Service
public class TarotDrawSessionService {

	private static final int CANDIDATE_COUNT = 5;

	private final TarotDrawSessionRepository repository;
	private final TarotDrawEntropy entropy;
	private final TarotDrawSessionProperties properties;
	private final Clock clock;

	@Autowired
	public TarotDrawSessionService(
		TarotDrawSessionRepository repository,
		TarotDrawEntropy entropy,
		TarotDrawSessionProperties properties
	) {
		this(repository, entropy, properties, Clock.systemUTC());
	}

	TarotDrawSessionService(
		TarotDrawSessionRepository repository,
		TarotDrawEntropy entropy,
		TarotDrawSessionProperties properties,
		Clock clock
	) {
		this.repository = repository;
		this.entropy = entropy;
		this.properties = properties;
		this.clock = clock;
	}

	public TarotDrawSessionView create(UUID userId, String spreadType) {
		TarotSpreadType spread = parseSpread(spreadType);
		Instant createdAt = clock.instant();
		Instant expiresAt = createdAt.plus(properties.ttl());
		List<String> remaining = MajorArcana.all();
		TarotDrawSessionState state = new TarotDrawSessionState(
			entropy.sessionId(), userId, spread, "in_progress", 0, 0, createdAt, expiresAt,
			remaining, List.of(), deal(remaining), null, null
		);
		if (!repository.create(state, properties.ttl())) {
			throw TarotDrawSessionException.active();
		}
		return toView(state);
	}

	public TarotDrawSessionView getActive(UUID userId) {
		return repository.findActive(userId)
			.map(this::toView)
			.orElseThrow(TarotDrawSessionException::notFound);
	}

	public TarotDrawSessionView select(UUID userId, String sessionId, String candidateToken) {
		if (candidateToken == null || candidateToken.isBlank()) {
			throw TarotDrawSessionException.invalidSelection();
		}
		TarotDrawSessionState current = repository.findOwned(userId, sessionId)
			.orElseThrow(TarotDrawSessionException::notFound);
		if (!"in_progress".equals(current.status())) {
			throw TarotDrawSessionException.stateConflict();
		}
		String selectedCard = current.candidates().get(candidateToken);
		if (selectedCard == null) {
			throw TarotDrawSessionException.stateConflict();
		}

		List<String> selected = new ArrayList<>(current.selectedCards());
		selected.add(selectedCard);
		List<String> remaining = new ArrayList<>(current.remainingCards());
		remaining.remove(selectedCard);
		boolean complete = selected.size() == current.spreadType().cardCount();
		TarotDrawSessionState updated = new TarotDrawSessionState(
			current.id(), current.userId(), current.spreadType(),
			complete ? "complete" : "in_progress",
			selected.size(), current.version() + 1, current.createdAt(), current.expiresAt(),
			remaining, selected, complete ? Map.of() : deal(remaining), null, null
		);
		if (!repository.select(current, updated)) {
			if (repository.findOwned(userId, sessionId).isEmpty()) {
				throw TarotDrawSessionException.notFound();
			}
			throw TarotDrawSessionException.stateConflict();
		}
		return toView(updated);
	}

	public void abandon(UUID userId, String sessionId) {
		if (!repository.abandon(userId, sessionId)) {
			if (repository.findOwned(userId, sessionId).isEmpty()) {
				throw TarotDrawSessionException.notFound();
			}
			throw TarotDrawSessionException.stateConflict();
		}
	}

	public CompletedTarotDraw resolveAndConsume(
		UUID userId,
		String sessionId,
		String requestedSpreadType,
		UUID requestId,
		String inputHash
	) {
		TarotDrawSessionState state = repository.findOwned(userId, sessionId)
			.orElseThrow(TarotDrawSessionException::notFound);
		TarotSpreadType requestedSpread = parseSpread(requestedSpreadType);
		if (state.spreadType() != requestedSpread) {
			throw TarotDrawSessionException.invalidSpread();
		}
		if (!state.complete() && !state.consumed()) {
			throw TarotDrawSessionException.stateConflict();
		}

		ConsumeResult result = repository.consume(userId, sessionId, requestId, inputHash);
		switch (result) {
			case NOT_FOUND -> throw TarotDrawSessionException.notFound();
			case STATE_CONFLICT -> throw TarotDrawSessionException.stateConflict();
			case ALREADY_CONSUMED -> throw TarotDrawSessionException.alreadyConsumed();
			case CONSUMED, IDEMPOTENT -> { }
		}
		return new CompletedTarotDraw(state.spreadType(), state.selectedCards());
	}

	public boolean finalizeConsumption(
		UUID userId,
		String sessionId,
		UUID requestId,
		String inputHash
	) {
		return repository.finalizeConsumption(userId, sessionId, requestId, inputHash);
	}

	public CompletedTarotDraw resolveCompleted(
		UUID userId,
		String sessionId,
		String requestedSpreadType,
		UUID requestId
	) {
		TarotDrawSessionState state = repository.findOwned(userId, sessionId)
			.orElseThrow(TarotDrawSessionException::notFound);
		if (state.spreadType() != parseSpread(requestedSpreadType)) {
			throw TarotDrawSessionException.invalidSpread();
		}
		if (state.consumed() && !requestId.equals(state.consumedRequestId())) {
			throw TarotDrawSessionException.alreadyConsumed();
		}
		if (!state.complete() && !state.consumed()) {
			throw TarotDrawSessionException.stateConflict();
		}
		return new CompletedTarotDraw(state.spreadType(), state.selectedCards());
	}

	private Map<String, String> deal(List<String> remaining) {
		List<String> cards = entropy.shuffledCopy(remaining).subList(0, CANDIDATE_COUNT);
		Map<String, String> candidates = new LinkedHashMap<>();
		for (String card : cards) {
			String token;
			do {
				token = entropy.candidateToken();
			} while (candidates.containsKey(token));
			candidates.put(token, card);
		}
		return candidates;
	}

	private TarotDrawSessionView toView(TarotDrawSessionState state) {
		boolean complete = state.complete() || state.consumed();
		return new TarotDrawSessionView(
			state.id(), state.spreadType().value(), complete ? "complete" : state.status(),
			complete ? null : state.spreadType().positions().get(state.positionIndex()).id(),
			state.selectedCards().size(), state.spreadType().cardCount(), state.expiresAt(),
			complete ? null : state.candidates().keySet().stream()
				.map(TarotDrawCandidateView::new).toList(),
			complete ? java.util.stream.IntStream.range(0, state.selectedCards().size())
				.mapToObj(index -> new TarotDrawCardView(
					state.spreadType().positions().get(index).id(),
					state.selectedCards().get(index),
					false
				)).toList() : null
		);
	}

	private TarotSpreadType parseSpread(String value) {
		try {
			return TarotSpreadType.fromValue(value);
		} catch (IllegalArgumentException exception) {
			throw TarotDrawSessionException.invalidSpread();
		}
	}
}
