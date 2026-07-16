package com.myeongro.api.domain.tarotdraw.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;

import com.myeongro.api.domain.reading.entity.MajorArcana;
import com.myeongro.api.domain.reading.entity.TarotSpreadType;
import com.myeongro.api.domain.tarotdraw.exception.TarotDrawSessionException;
import com.myeongro.api.domain.tarotdraw.repository.TarotDrawSessionRepository;
import com.myeongro.api.domain.tarotdraw.repository.TarotDrawSessionState;

class TarotDrawSessionServiceTests {

	private static final UUID USER_ID = UUID.fromString("3b413be2-2b81-4802-8c6a-f868a85d8d83");
	private static final Instant NOW = Instant.parse("2026-07-17T00:00:00Z");

	@ParameterizedTest
	@EnumSource(TarotSpreadType.class)
	void dealsFiveOpaqueCandidatesAndRevealsCardsOnlyAfterFinalSelection(
		TarotSpreadType spread
	) {
		InMemoryRepository repository = new InMemoryRepository();
		TarotDrawSessionService service = service(repository);

		TarotDrawSessionView view = service.create(USER_ID, spread.value());
		assertThat(view.candidates()).hasSize(5);
		assertThat(view.cards()).isNull();
		assertThat(view.candidates()).allSatisfy(candidate ->
			assertThat(candidate.token()).startsWith("token-"));

		List<String> selectedCards = new ArrayList<>();
		for (int index = 0; index < spread.cardCount(); index++) {
			TarotDrawSessionState before = repository.state;
			String token = view.candidates().get(0).token();
			selectedCards.add(before.candidates().get(token));
			view = service.select(USER_ID, view.drawSessionId(), token);
			if (index + 1 < spread.cardCount()) {
				assertThat(view.candidates()).hasSize(5);
				assertThat(view.cards()).isNull();
				assertThat(repository.state.remainingCards())
					.doesNotContain(selectedCards.get(index));
			}
		}

		assertThat(view.status()).isEqualTo("complete");
		assertThat(view.candidates()).isNull();
		assertThat(view.cards()).extracting(TarotDrawCardView::position)
			.containsExactlyElementsOf(spread.positions().stream().map(position -> position.id()).toList());
		assertThat(view.cards()).extracting(TarotDrawCardView::cardId)
			.containsExactlyElementsOf(selectedCards);
	}

	@Test
	void keepsUnselectedCandidatesEligibleForTheNextPosition() {
		InMemoryRepository repository = new InMemoryRepository();
		TarotDrawSessionService service = service(repository);
		TarotDrawSessionView first = service.create(USER_ID, "mind_three_card");
		List<String> firstTokens = first.candidates().stream().map(TarotDrawCandidateView::token).toList();
		List<String> firstCards = firstTokens.stream().map(repository.state.candidates()::get).toList();

		TarotDrawSessionView second = service.select(USER_ID, first.drawSessionId(), firstTokens.get(0));
		List<String> secondCards = second.candidates().stream()
			.map(candidate -> repository.state.candidates().get(candidate.token()))
			.toList();

		assertThat(secondCards).containsAll(firstCards.subList(1, 5));
		assertThat(secondCards).doesNotContain(firstCards.get(0));
	}

	@Test
	void restoresActiveStateAndAllowsRecreationAfterAbandonment() {
		InMemoryRepository repository = new InMemoryRepository();
		TarotDrawSessionService service = service(repository);
		TarotDrawSessionView created = service.create(USER_ID, "daily_one_card");

		assertThat(service.getActive(USER_ID)).isEqualTo(created);
		assertThatThrownBy(() -> service.create(USER_ID, "daily_one_card"))
			.isInstanceOfSatisfying(TarotDrawSessionException.class, exception ->
				assertThat(exception.code()).isEqualTo("DRAW_SESSION_ACTIVE"));

		service.abandon(USER_ID, created.drawSessionId());
		assertThat(service.create(USER_ID, "daily_one_card").drawSessionId())
			.isNotEqualTo(created.drawSessionId());
	}

	@Test
	void rejectsStaleTokenAndDoubleAdvancement() {
		InMemoryRepository repository = new InMemoryRepository();
		TarotDrawSessionService service = service(repository);
		TarotDrawSessionView created = service.create(USER_ID, "mind_three_card");
		String token = created.candidates().get(0).token();

		service.select(USER_ID, created.drawSessionId(), token);

		assertThatThrownBy(() -> service.select(USER_ID, created.drawSessionId(), token))
			.isInstanceOfSatisfying(TarotDrawSessionException.class, exception ->
				assertThat(exception.code()).isEqualTo("DRAW_SESSION_STATE_CONFLICT"));
	}

	@Test
	void recoversCompletedResultFromActiveAfterFinalResponseIsLost() {
		InMemoryRepository repository = new InMemoryRepository();
		TarotDrawSessionService service = service(repository);
		TarotDrawSessionView created = service.create(USER_ID, "daily_one_card");
		String token = created.candidates().get(0).token();

		TarotDrawSessionView completed = service.select(USER_ID, created.drawSessionId(), token);

		assertThatThrownBy(() -> service.select(USER_ID, created.drawSessionId(), token))
			.isInstanceOfSatisfying(TarotDrawSessionException.class, exception ->
				assertThat(exception.code()).isEqualTo("DRAW_SESSION_STATE_CONFLICT"));
		assertThat(service.getActive(USER_ID)).isEqualTo(completed);
		assertThat(completed.cards()).hasSize(1);
	}

	@Test
	void consumesCompletedSessionOnceAndAllowsOnlySameIdempotentRequest() {
		InMemoryRepository repository = new InMemoryRepository();
		TarotDrawSessionService service = service(repository);
		TarotDrawSessionView created = service.create(USER_ID, "daily_one_card");
		service.select(USER_ID, created.drawSessionId(), created.candidates().get(0).token());
		UUID requestId = UUID.randomUUID();

		CompletedTarotDraw first = service.resolveAndConsume(
			USER_ID, created.drawSessionId(), "daily_one_card", requestId, "hash"
		);
		CompletedTarotDraw repeated = service.resolveAndConsume(
			USER_ID, created.drawSessionId(), "daily_one_card", requestId, "hash"
		);

		assertThat(repeated).isEqualTo(first);
		assertThatThrownBy(() -> service.resolveAndConsume(
			USER_ID, created.drawSessionId(), "daily_one_card", UUID.randomUUID(), "hash"
		)).isInstanceOfSatisfying(TarotDrawSessionException.class, exception ->
			assertThat(exception.code()).isEqualTo("DRAW_SESSION_ALREADY_CONSUMED"));
	}

	private TarotDrawSessionService service(InMemoryRepository repository) {
		return new TarotDrawSessionService(
			repository,
			new DeterministicEntropy(),
			new TarotDrawSessionProperties("myeongro:test:draw", Duration.ofMinutes(30)),
			Clock.fixed(NOW, ZoneOffset.UTC)
		);
	}

	private static final class DeterministicEntropy implements TarotDrawEntropy {
		private int sequence;

		@Override
		public String sessionId() {
			return "session-" + sequence++;
		}

		@Override
		public String candidateToken() {
			return "token-" + sequence++;
		}

		@Override
		public List<String> shuffledCopy(List<String> cards) {
			return List.copyOf(cards);
		}
	}

	private static final class InMemoryRepository implements TarotDrawSessionRepository {
		private TarotDrawSessionState state;

		@Override
		public synchronized boolean create(TarotDrawSessionState newState, Duration ttl) {
			if (state != null) return false;
			state = newState;
			return true;
		}

		@Override
		public synchronized Optional<TarotDrawSessionState> findActive(UUID userId) {
			return state != null && state.userId().equals(userId) && !state.consumed()
				? Optional.of(state) : Optional.empty();
		}

		@Override
		public synchronized Optional<TarotDrawSessionState> findOwned(UUID userId, String sessionId) {
			return state != null && state.userId().equals(userId) && state.id().equals(sessionId)
				? Optional.of(state) : Optional.empty();
		}

		@Override
		public synchronized boolean select(TarotDrawSessionState expected, TarotDrawSessionState updated) {
			if (state == null || state.version() != expected.version()
				|| !state.candidates().equals(expected.candidates())) return false;
			state = updated;
			return true;
		}

		@Override
		public synchronized boolean abandon(UUID userId, String sessionId) {
			if (state == null || !state.userId().equals(userId) || !state.id().equals(sessionId)
				|| state.consumed()) return false;
			state = null;
			return true;
		}

		@Override
		public synchronized ConsumeResult consume(
			UUID userId, String sessionId, UUID requestId, String inputHash
		) {
			if (state == null || !state.userId().equals(userId) || !state.id().equals(sessionId)) {
				return ConsumeResult.NOT_FOUND;
			}
			if (state.consumed()) {
				if (!state.consumedRequestId().equals(requestId)) return ConsumeResult.ALREADY_CONSUMED;
				return state.consumedInputHash().equals(inputHash)
					? ConsumeResult.IDEMPOTENT : ConsumeResult.STATE_CONFLICT;
			}
			if (!state.complete()) return ConsumeResult.STATE_CONFLICT;
			state = state.consumedBy(requestId, inputHash);
			return ConsumeResult.CONSUMED;
		}
	}
}
