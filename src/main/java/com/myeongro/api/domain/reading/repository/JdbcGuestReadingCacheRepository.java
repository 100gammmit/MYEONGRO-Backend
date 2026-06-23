package com.myeongro.api.domain.reading.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.reading.dto.CreatedReadingResponse;
import com.myeongro.api.domain.reading.dto.ReadingResult;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.exception.FreeReadingIdempotencyConflictException;
import com.myeongro.api.domain.reading.exception.FreeReadingQuotaExceededException;

@Repository
public class JdbcGuestReadingCacheRepository implements GuestReadingCacheRepository {

	private static final String CREATE_PENDING = """
		select cache_id, created
		from public.create_pending_guest_reading_cache(
			?, ?, ?, ?, cast(? as public.reading_kind), cast(? as jsonb)
		)
		""";
	private static final String SELECT_CACHE = """
		select
			id,
			kind::text as kind,
			status::text as status,
			title,
			input::text as input,
			result::text as result,
			error_code,
			created_at,
			updated_at
		from public.guest_reading_cache
		where id = ?
		""";
	private static final String COMPLETE_PENDING = """
		select public.complete_guest_reading_cache(?, ?, cast(? as jsonb))
		""";
	private static final String FAIL_PENDING = """
		select public.fail_guest_reading_cache(?, ?)
		""";

	private final JdbcTemplate jdbcTemplate;
	private final ObjectMapper objectMapper;

	public JdbcGuestReadingCacheRepository(
		JdbcTemplate jdbcTemplate,
		ObjectMapper objectMapper
	) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
	}

	@Override
	public PendingGuestReadingCache createPending(PendingReadingCommand command) {
		try {
			UUID cacheId = jdbcTemplate.queryForObject(
				CREATE_PENDING,
				(resultSet, rowNumber) -> resultSet.getObject("cache_id", UUID.class),
				command.guestSessionId(),
				command.ipHash(),
				command.requestId(),
				command.inputHash(),
				command.kind().value(),
				toJson(command.input())
			);
			if (cacheId == null) {
				throw new IllegalStateException("Pending guest reading cache was not created");
			}
			return new PendingGuestReadingCache(cacheId, findCachedReading(cacheId));
		} catch (DataAccessException exception) {
			throw mapDatabaseException(exception);
		}
	}

	@Override
	public CreatedReadingResponse completePending(
		PendingGuestReadingCache pending,
		ReadingResult result
	) {
		try {
			jdbcTemplate.queryForObject(
				COMPLETE_PENDING,
				Object.class,
				pending.cacheId(),
				result.title(),
				toJson(result)
			);
			return findCachedReading(pending.cacheId());
		} catch (DataAccessException exception) {
			throw mapDatabaseException(exception);
		}
	}

	@Override
	public void failPending(PendingGuestReadingCache pending, String errorCode) {
		try {
			jdbcTemplate.queryForObject(
				FAIL_PENDING,
				Object.class,
				pending.cacheId(),
				errorCode
			);
		} catch (DataAccessException exception) {
			throw mapDatabaseException(exception);
		}
	}

	private CreatedReadingResponse findCachedReading(UUID cacheId) {
		return jdbcTemplate.queryForObject(
			SELECT_CACHE,
			this::toResponse,
			cacheId
		);
	}

	private CreatedReadingResponse toResponse(
		ResultSet resultSet,
		int rowNumber
	) throws SQLException {
		return new CreatedReadingResponse(
			resultSet.getObject("id", UUID.class),
			ReadingKind.fromValue(resultSet.getString("kind")),
			resultSet.getString("status"),
			resultSet.getString("title"),
			fromJson(resultSet.getString("input")),
			nullableJson(resultSet.getString("result")),
			resultSet.getString("error_code"),
			toInstant(resultSet.getTimestamp("created_at")),
			toInstant(resultSet.getTimestamp("updated_at"))
		);
	}

	private String toJson(Map<String, Object> input) {
		try {
			return objectMapper.writeValueAsString(input);
		} catch (JsonProcessingException exception) {
			throw new IllegalArgumentException("Invalid reading input", exception);
		}
	}

	private String toJson(ReadingResult result) {
		try {
			return objectMapper.writeValueAsString(result);
		} catch (JsonProcessingException exception) {
			throw new IllegalArgumentException("Invalid reading result", exception);
		}
	}

	private Map<String, Object> fromJson(String json) {
		try {
			return objectMapper.readValue(json, new TypeReference<>() {
			});
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Cannot parse guest reading cache JSON", exception);
		}
	}

	private Map<String, Object> nullableJson(String json) {
		return json == null ? null : fromJson(json);
	}

	private java.time.Instant toInstant(Timestamp timestamp) {
		return timestamp == null ? null : timestamp.toInstant();
	}

	private RuntimeException mapDatabaseException(DataAccessException exception) {
		String sqlState = findSqlState(exception);
		if ("RL101".equals(sqlState) || "RL102".equals(sqlState) || "RL103".equals(sqlState)) {
			return new FreeReadingQuotaExceededException();
		}
		if ("RL104".equals(sqlState)) {
			return new FreeReadingIdempotencyConflictException();
		}
		return exception;
	}

	private String findSqlState(Throwable throwable) {
		Throwable current = throwable;
		while (current != null) {
			if (current instanceof SQLException sqlException) {
				return sqlException.getSQLState();
			}
			current = current.getCause();
		}
		return null;
	}
}
