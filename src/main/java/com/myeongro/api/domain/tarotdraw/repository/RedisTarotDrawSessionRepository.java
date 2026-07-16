package com.myeongro.api.domain.tarotdraw.repository;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.tarotdraw.service.TarotDrawSessionProperties;

@Repository
public class RedisTarotDrawSessionRepository implements TarotDrawSessionRepository {

	private static final DefaultRedisScript<Long> CREATE_SCRIPT = new DefaultRedisScript<>("""
		local active = redis.call('GET', KEYS[1])
		if active then
		  if redis.call('EXISTS', ARGV[4] .. active) == 1 then
		    return 0
		  end
		  redis.call('DEL', KEYS[1])
		end
		redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[3])
		redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[3])
		return 1
		""", Long.class);

	private static final DefaultRedisScript<String> SELECT_SCRIPT = new DefaultRedisScript<>("""
		local raw = redis.call('GET', KEYS[1])
		if not raw then return 'NOT_FOUND' end
		local state = cjson.decode(raw)
		if state.userId ~= ARGV[1] then return 'NOT_FOUND' end
		if state.status ~= 'in_progress' then return 'CONFLICT' end
		if tostring(state.version) ~= ARGV[2] then return 'CONFLICT' end
		if not state.candidates[ARGV[3]] then return 'CONFLICT' end
		redis.call('SET', KEYS[1], ARGV[4], 'KEEPTTL')
		return 'UPDATED'
		""", String.class);

	private static final DefaultRedisScript<String> ABANDON_SCRIPT = new DefaultRedisScript<>("""
		local raw = redis.call('GET', KEYS[1])
		if not raw then return 'NOT_FOUND' end
		local state = cjson.decode(raw)
		if state.userId ~= ARGV[1] then return 'NOT_FOUND' end
		if state.status == 'consumed' then return 'CONFLICT' end
		redis.call('DEL', KEYS[1])
		if redis.call('GET', KEYS[2]) == ARGV[2] then redis.call('DEL', KEYS[2]) end
		return 'DELETED'
		""", String.class);

	private static final DefaultRedisScript<String> CONSUME_SCRIPT = new DefaultRedisScript<>("""
		local raw = redis.call('GET', KEYS[1])
		if not raw then return 'NOT_FOUND' end
		local state = cjson.decode(raw)
		if state.userId ~= ARGV[1] then return 'NOT_FOUND' end
		if state.status == 'consumed' then
		  if state.consumedRequestId ~= ARGV[2] then return 'ALREADY_CONSUMED' end
		  if state.consumedInputHash ~= ARGV[3] then return 'STATE_CONFLICT' end
		  return 'IDEMPOTENT'
		end
		if state.status ~= 'complete' then return 'STATE_CONFLICT' end
		redis.call('SET', KEYS[1], ARGV[5], 'KEEPTTL')
		if redis.call('GET', KEYS[2]) == ARGV[4] then redis.call('DEL', KEYS[2]) end
		return 'CONSUMED'
		""", String.class);

	private static final DefaultRedisScript<Long> CLEAN_ACTIVE_SCRIPT = new DefaultRedisScript<>("""
		if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end
		return 0
		""", Long.class);

	private final StringRedisTemplate redis;
	private final ObjectMapper objectMapper;
	private final String namespace;

	public RedisTarotDrawSessionRepository(
		StringRedisTemplate redis,
		ObjectMapper objectMapper,
		TarotDrawSessionProperties properties
	) {
		this.redis = redis;
		this.objectMapper = objectMapper;
		this.namespace = properties.namespace();
	}

	@Override
	public boolean create(TarotDrawSessionState state, Duration ttl) {
		Long result = redis.execute(
			CREATE_SCRIPT,
			java.util.List.of(activeKey(state.userId()), sessionKey(state.id())),
			state.id(), toJson(state), Long.toString(ttl.toMillis()), sessionPrefix()
		);
		return Long.valueOf(1).equals(result);
	}

	@Override
	public Optional<TarotDrawSessionState> findActive(UUID userId) {
		String activeId = redis.opsForValue().get(activeKey(userId));
		if (activeId == null) {
			return Optional.empty();
		}
		Optional<TarotDrawSessionState> state = findOwned(userId, activeId);
		if (state.isEmpty() || state.get().consumed()) {
			redis.execute(CLEAN_ACTIVE_SCRIPT, java.util.List.of(activeKey(userId)), activeId);
			return Optional.empty();
		}
		return state;
	}

	@Override
	public Optional<TarotDrawSessionState> findOwned(UUID userId, String sessionId) {
		String json = redis.opsForValue().get(sessionKey(sessionId));
		if (json == null) {
			return Optional.empty();
		}
		TarotDrawSessionState state = fromJson(json);
		return state.userId().equals(userId) ? Optional.of(state) : Optional.empty();
	}

	@Override
	public boolean select(TarotDrawSessionState expected, TarotDrawSessionState updated) {
		String result = redis.execute(
			SELECT_SCRIPT,
			java.util.List.of(sessionKey(expected.id())),
			expected.userId().toString(), Long.toString(expected.version()),
			selectedToken(expected, updated), toJson(updated)
		);
		return "UPDATED".equals(result);
	}

	@Override
	public boolean abandon(UUID userId, String sessionId) {
		String result = redis.execute(
			ABANDON_SCRIPT,
			java.util.List.of(sessionKey(sessionId), activeKey(userId)),
			userId.toString(), sessionId
		);
		return "DELETED".equals(result);
	}

	@Override
	public ConsumeResult consume(UUID userId, String sessionId, UUID requestId, String inputHash) {
		String consumedJson = findOwned(userId, sessionId)
			.map(state -> toJson(state.consumedBy(requestId, inputHash)))
			.orElse("{}");
		String result = redis.execute(
			CONSUME_SCRIPT,
			java.util.List.of(sessionKey(sessionId), activeKey(userId)),
			userId.toString(), requestId.toString(), inputHash, sessionId, consumedJson
		);
		return result == null ? ConsumeResult.STATE_CONFLICT : ConsumeResult.valueOf(result);
	}

	private String selectedToken(TarotDrawSessionState expected, TarotDrawSessionState updated) {
		String selectedCard = updated.selectedCards().get(updated.selectedCards().size() - 1);
		return expected.candidates().entrySet().stream()
			.filter(entry -> entry.getValue().equals(selectedCard))
			.map(java.util.Map.Entry::getKey)
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("Selected card token is missing"));
	}

	private String toJson(TarotDrawSessionState state) {
		try {
			return objectMapper.writeValueAsString(state);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Cannot serialize tarot draw session", exception);
		}
	}

	private TarotDrawSessionState fromJson(String json) {
		try {
			return objectMapper.readValue(json, TarotDrawSessionState.class);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Cannot deserialize tarot draw session", exception);
		}
	}

	private String activeKey(UUID userId) {
		return namespace + ":active:" + userId;
	}

	private String sessionKey(String sessionId) {
		return sessionPrefix() + sessionId;
	}

	private String sessionPrefix() {
		return namespace + ":session:";
	}
}
