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

@Repository
public class JdbcReadingCreationRepository implements ReadingCreationRepository {

	private static final String CREATE_PENDING = """
		select reading_id, generation_id, created
		from public.create_pending_free_reading(
			?, ?, ?, ?, ?, cast(? as public.reading_kind), cast(? as jsonb), ?, ?, ?
		)
		""";
	private static final String SELECT_READING = """
		select
			id,
			kind::text as kind,
			status::text as status,
			title,
			input::text as input,
			result::text as result,
			created_at,
			updated_at
		from public.readings
		where id = ?
		""";
	private static final String COMPLETE_PENDING = """
		select public.complete_free_reading_generation(?, ?, ?, cast(? as jsonb))
		""";
	private static final String FAIL_PENDING = """
		select public.fail_free_reading_generation(?, ?, ?)
		""";

	private final JdbcTemplate jdbcTemplate;
	private final ObjectMapper objectMapper;

	public JdbcReadingCreationRepository(
		JdbcTemplate jdbcTemplate,
		ObjectMapper objectMapper
	) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
	}

	@Override
	public PendingReadingCreation createPending(PendingReadingCommand command) {
		try {
			PendingIds pendingIds = jdbcTemplate.queryForObject(
				CREATE_PENDING,
				(resultSet, rowNumber) -> new PendingIds(
					resultSet.getObject("reading_id", UUID.class),
					resultSet.getObject("generation_id", UUID.class)
				),
				command.userId(),
				command.guestSessionId(),
				command.ipHash(),
				command.requestId(),
				command.inputHash(),
				command.kind().value(),
				toJson(command.input()),
				command.provider(),
				command.model(),
				command.promptVersion()
			);
			if (pendingIds == null || pendingIds.readingId() == null) {
				throw new IllegalStateException("Pending reading was not created");
			}
			return new PendingReadingCreation(
				pendingIds.readingId(),
				pendingIds.generationId(),
				findCreatedReading(pendingIds.readingId())
			);
		} catch (DataAccessException exception) {
			throw mapDatabaseException(exception);
		}
	}

	@Override
	public CreatedReadingResponse completePending(
		PendingReadingCreation pending,
		ReadingResult result
	) {
		try {
			jdbcTemplate.queryForObject(
				COMPLETE_PENDING,
				Object.class,
				pending.readingId(),
				pending.generationId(),
				result.title(),
				toJson(result)
			);
			return findCreatedReading(pending.readingId());
		} catch (DataAccessException exception) {
			throw mapDatabaseException(exception);
		}
	}

	@Override
	public void failPending(PendingReadingCreation pending, String errorCode) {
		try {
			jdbcTemplate.queryForObject(
				FAIL_PENDING,
				Object.class,
				pending.readingId(),
				pending.generationId(),
				errorCode
			);
		} catch (DataAccessException exception) {
			throw mapDatabaseException(exception);
		}
	}

	private CreatedReadingResponse findCreatedReading(UUID readingId) {
		return jdbcTemplate.queryForObject(
			SELECT_READING,
			this::toResponse,
			readingId
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
			null,
			toInstant(resultSet.getTimestamp("created_at")),
			toInstant(resultSet.getTimestamp("updated_at"))
		);
	}

	private java.time.Instant toInstant(Timestamp timestamp) {
		return timestamp == null ? null : timestamp.toInstant();
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
			throw new IllegalStateException("Cannot parse reading input", exception);
		}
	}

	private Map<String, Object> nullableJson(String json) {
		return json == null ? null : fromJson(json);
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

	private record PendingIds(UUID readingId, UUID generationId) {
	}
}
