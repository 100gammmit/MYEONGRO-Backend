package com.myeongro.api.domain.reading.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.reading.dto.CreatedReadingResponse;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.exception.ReadingRetryNotAllowedException;
import com.myeongro.api.domain.reading.service.ReadingGenerationMetadata;

public class JdbcReadingRecordsRepository implements ReadingRecordsRepository {

	private static final String SELECT_ACTIVE_READING = """
		select
			r.id,
			r.kind::text as kind,
			r.status::text as status,
			r.title,
			r.input::text as input,
			r.result::text as result,
			gr.error_code,
			r.created_at,
			r.updated_at
		from public.readings r
		left join lateral (
			select error_code
			from public.generation_records
			where reading_id = r.id
			order by created_at desc
			limit 1
		) gr on true
		where r.user_id = ?
		  and r.deleted_at is null
		""";
	private static final String LIST_BY_USER = SELECT_ACTIVE_READING + """
		order by r.created_at desc
		""";
	private static final String FIND_BY_USER_AND_ID = SELECT_ACTIVE_READING + """
		  and r.id = ?
		""";
	private static final String SOFT_DELETE = """
		update public.readings
		set deleted_at = now()
		where user_id = ?
		  and id = ?
		  and deleted_at is null
		""";
	private static final String START_FAILED_RETRY = """
		select generation_id
		from public.start_failed_reading_retry(?, ?, ?, ?, ?)
		""";

	private final JdbcTemplate jdbcTemplate;
	private final ObjectMapper objectMapper;

	public JdbcReadingRecordsRepository(
		JdbcTemplate jdbcTemplate,
		ObjectMapper objectMapper
	) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
	}

	@Override
	public List<CreatedReadingResponse> listByUser(UUID userId) {
		return jdbcTemplate.query(LIST_BY_USER, this::toResponse, userId);
	}

	@Override
	public Optional<CreatedReadingResponse> findByUserAndId(UUID userId, UUID readingId) {
		List<CreatedReadingResponse> readings = jdbcTemplate.query(
			FIND_BY_USER_AND_ID,
			this::toResponse,
			userId,
			readingId
		);
		return readings.stream().findFirst();
	}

	@Override
	public boolean softDeleteByUserAndId(UUID userId, UUID readingId) {
		return jdbcTemplate.update(SOFT_DELETE, userId, readingId) > 0;
	}

	@Override
	public PendingReadingCreation startFailedRetry(
		UUID userId,
		UUID readingId,
		ReadingGenerationMetadata metadata
	) {
		try {
			Long generationId = jdbcTemplate.queryForObject(
				START_FAILED_RETRY,
				Long.class,
				userId,
				readingId,
				metadata.provider(),
				metadata.model(),
				metadata.promptVersion()
			);
			CreatedReadingResponse reading = findByUserAndId(userId, readingId)
				.orElseThrow(ReadingRetryNotAllowedException::new);
			return new PendingReadingCreation(readingId, generationId, reading);
		} catch (DataAccessException exception) {
			if ("RL109".equals(findSqlState(exception))) {
				throw new ReadingRetryNotAllowedException();
			}
			throw exception;
		}
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

	private Map<String, Object> fromJson(String json) {
		try {
			return objectMapper.readValue(json, new TypeReference<>() {
			});
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Cannot parse reading JSON", exception);
		}
	}

	private Map<String, Object> nullableJson(String json) {
		return json == null ? null : fromJson(json);
	}

	private java.time.Instant toInstant(Timestamp timestamp) {
		return timestamp == null ? null : timestamp.toInstant();
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
