package com.myeongro.api.domain.reading.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.reading.dto.CreatedReadingResponse;
import com.myeongro.api.domain.reading.dto.GeneratedReading;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.exception.InsufficientReadingCreditsException;
import com.myeongro.api.domain.reading.exception.ReadingGenerationInProgressException;
import com.myeongro.api.domain.reading.exception.ReadingIdempotencyConflictException;
import com.myeongro.api.domain.readingcredit.config.ReadingCreditProperties;

@Repository
public class JdbcReadingCreationRepository implements ReadingCreationRepository {
	private static final Logger log = LoggerFactory.getLogger(
		JdbcReadingCreationRepository.class
	);

	private static final String SELECT_EXISTING = """
		select
			id,
			kind::text as kind,
			spread_type,
			schema_version,
			status::text as status,
			title,
			input_payload::text as input_payload,
			result_payload::text as result_payload,
			input_hash,
			created_at,
			updated_at
		from public.readings
		where user_id = ? and request_id = ? and deleted_at is null
		""";

	private static final String CREATE_PENDING = """
		select reading_id, generation_id, created
		from public.create_pending_reading(
			?, ?, ?, cast(? as public.reading_kind), ?, ?, cast(? as jsonb), ?, ?, ?, ?, ?
		)
		""";
	private static final String SELECT_READING = """
		select
			id,
			kind::text as kind,
			spread_type,
			schema_version,
			status::text as status,
			title,
			input_payload::text as input_payload,
			result_payload::text as result_payload,
			created_at,
			updated_at
		from public.readings
		where id = ?
		""";
	private static final String COMPLETE_PENDING = """
		select public.complete_reading_generation(?, ?, ?, cast(? as jsonb), ?)
		""";
	private static final String FAIL_PENDING = """
		select public.fail_reading_generation(?, ?, ?)
		""";

	private final JdbcTemplate jdbcTemplate;
	private final ObjectMapper objectMapper;
	private final ReadingCreditProperties creditProperties;

	public JdbcReadingCreationRepository(
		JdbcTemplate jdbcTemplate,
		ObjectMapper objectMapper,
		ReadingCreditProperties creditProperties
	) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
		this.creditProperties = creditProperties;
	}

	@Override
	public Optional<CreatedReadingResponse> findExisting(
		UUID userId,
		UUID requestId,
		String inputHash
	) {
		List<ExistingReading> existing = jdbcTemplate.query(
			SELECT_EXISTING,
			(resultSet, rowNumber) -> new ExistingReading(
				toResponse(resultSet, rowNumber),
				resultSet.getString("input_hash")
			),
			userId,
			requestId
		);
		if (existing.isEmpty()) {
			return Optional.empty();
		}
		ExistingReading reading = existing.getFirst();
		if (!inputHash.equals(reading.inputHash())) {
			throw new ReadingIdempotencyConflictException();
		}
		return Optional.of(reading.response());
	}

	@Override
	public PendingReadingCreation createPending(PendingReadingCommand command) {
		try {
			PendingIds pendingIds = jdbcTemplate.queryForObject(
				CREATE_PENDING,
				(resultSet, rowNumber) -> new PendingIds(
					resultSet.getObject("reading_id", UUID.class),
					resultSet.getObject("generation_id", Long.class)
				),
				command.userId(),
				command.requestId(),
				command.inputHash(),
				command.kind().value(),
				command.spreadType(),
				command.schemaVersion(),
				toJson(command.input()),
				command.provider(),
				command.model(),
				command.promptVersion(),
				command.creditCost(),
				creditProperties.dailyFreeGrant()
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
			throw mapDatabaseException(
				exception, command.userId(), command.creditCost()
			);
		}
	}

	@Override
	public CreatedReadingResponse completePending(
		PendingReadingCreation pending,
		GeneratedReading result
	) {
		try {
			Boolean balanceMismatch = jdbcTemplate.queryForObject(
				COMPLETE_PENDING,
				Boolean.class,
				pending.readingId(),
				pending.generationId(),
				result.title(),
				toJson(result.payload()),
				creditProperties.dailyFreeGrant()
			);
			if (Boolean.TRUE.equals(balanceMismatch)) {
				log.error(
					"CREDIT_COMPLETION_BALANCE_MISMATCH readingId={} generationId={}",
					pending.readingId(), pending.generationId()
				);
			}
			return findCreatedReading(pending.readingId());
		} catch (DataAccessException exception) {
			throw mapDatabaseException(exception, null, 0);
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
			resultSet.getString("spread_type"),
			resultSet.getInt("schema_version"),
			resultSet.getString("status"),
			resultSet.getString("title"),
			fromJson(resultSet.getString("input_payload")),
			nullableJson(resultSet.getString("result_payload")),
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
		return mapDatabaseException(exception, null, 0);
	}

	private RuntimeException mapDatabaseException(
		DataAccessException exception,
		UUID userId,
		int requiredCredits
	) {
		String sqlState = findSqlState(exception);
		if ("RL104".equals(sqlState)) {
			return new ReadingIdempotencyConflictException();
		}
		if ("RL110".equals(sqlState) || "RL111".equals(sqlState)) {
			return new ReadingGenerationInProgressException();
		}
		if ("RL112".equals(sqlState)) {
			return new InsufficientReadingCreditsException(userId, requiredCredits);
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

	private record PendingIds(UUID readingId, Long generationId) {
	}

	private record ExistingReading(CreatedReadingResponse response, String inputHash) {
	}
}
