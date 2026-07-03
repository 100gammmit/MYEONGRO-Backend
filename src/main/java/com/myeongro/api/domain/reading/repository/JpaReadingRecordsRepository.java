package com.myeongro.api.domain.reading.repository;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.reading.dto.CreatedReadingResponse;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.exception.ReadingRetryNotAllowedException;
import com.myeongro.api.domain.reading.service.ReadingGenerationMetadata;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

@Primary
@Repository
public class JpaReadingRecordsRepository implements ReadingRecordsRepository {

	private static final String SELECT_ACTIVE_READING = """
		select
			r.id,
			cast(r.kind as varchar) as kind,
			cast(r.status as varchar) as status,
			r.title,
			cast(r.input as varchar) as input,
			cast(r.result as varchar) as result,
			(
				select gr.error_code
				from public.generation_records gr
				where gr.reading_id = r.id
				order by gr.created_at desc
				limit 1
			) as error_code,
			r.created_at,
			r.updated_at
		from public.readings r
		where r.user_id = :userId
		  and r.deleted_at is null
		""";
	private static final String START_FAILED_RETRY = """
		select generation_id
		from public.start_failed_reading_retry(?, ?, ?, ?, ?)
		""";

	@PersistenceContext
	private EntityManager entityManager;

	private final JdbcTemplate jdbcTemplate;
	private final ObjectMapper objectMapper;

	public JpaReadingRecordsRepository(
		JdbcTemplate jdbcTemplate,
		ObjectMapper objectMapper
	) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
	}

	@Override
	@Transactional(readOnly = true)
	public List<CreatedReadingResponse> listByUser(UUID userId) {
		Query query = entityManager.createNativeQuery(SELECT_ACTIVE_READING + """
			order by r.created_at desc
			""");
		query.setParameter("userId", userId);
		return query.getResultList().stream()
			.map(row -> toResponse((Object[]) row))
			.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<CreatedReadingResponse> findByUserAndId(UUID userId, UUID readingId) {
		Query query = entityManager.createNativeQuery(SELECT_ACTIVE_READING + """
			  and r.id = :readingId
			""");
		query.setParameter("userId", userId);
		query.setParameter("readingId", readingId);
		return query.getResultList().stream()
			.findFirst()
			.map(row -> toResponse((Object[]) row));
	}

	@Override
	@Transactional
	public boolean softDeleteByUserAndId(UUID userId, UUID readingId) {
		int updated = entityManager.createNativeQuery("""
			update public.readings
			set deleted_at = current_timestamp
			where user_id = :userId
			  and id = :readingId
			  and deleted_at is null
			""")
			.setParameter("userId", userId)
			.setParameter("readingId", readingId)
			.executeUpdate();
		return updated > 0;
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

	private CreatedReadingResponse toResponse(Object[] row) {
		return new CreatedReadingResponse(
			toUuid(row[0]),
			ReadingKind.fromValue((String) row[1]),
			(String) row[2],
			(String) row[3],
			fromJson((String) row[4]),
			nullableJson((String) row[5]),
			(String) row[6],
			toInstant(row[7]),
			toInstant(row[8])
		);
	}

	private UUID toUuid(Object value) {
		if (value instanceof UUID uuid) {
			return uuid;
		}
		if (value instanceof byte[] bytes) {
			ByteBuffer buffer = ByteBuffer.wrap(bytes);
			return new UUID(buffer.getLong(), buffer.getLong());
		}
		return UUID.fromString(value.toString());
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

	private Instant toInstant(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Instant instant) {
			return instant;
		}
		if (value instanceof Timestamp timestamp) {
			return timestamp.toInstant();
		}
		if (value instanceof OffsetDateTime offsetDateTime) {
			return offsetDateTime.toInstant();
		}
		if (value instanceof LocalDateTime localDateTime) {
			return localDateTime.toInstant(ZoneOffset.UTC);
		}
		throw new IllegalStateException(
			"Unsupported timestamp value type: " + value.getClass().getName()
		);
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
