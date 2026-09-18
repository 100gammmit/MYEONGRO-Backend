package com.myeongro.api.domain.reading.repository;

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

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.reading.dto.CreatedReadingResponse;
import com.myeongro.api.domain.reading.entity.ReadingKind;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

@Repository
public class JpaReadingRecordsRepository implements ReadingRecordsRepository {

	private static final String SELECT_ACTIVE_READING = """
		select
			r.id,
			cast(r.kind as varchar) as kind,
			r.spread_type,
			r.schema_version,
			cast(r.status as varchar) as status,
			r.title,
			cast(r.input_payload as varchar) as input_payload,
			cast(r.result_payload as varchar) as result_payload,
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
		""";

	@PersistenceContext
	private EntityManager entityManager;

	private final ObjectMapper objectMapper;

	public JpaReadingRecordsRepository(ObjectMapper objectMapper) {
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
	public boolean deleteByUserAndId(UUID userId, UUID readingId) {
		int deleted = entityManager.createNativeQuery("""
			delete from public.readings
			where user_id = :userId
			  and id = :readingId
			""")
			.setParameter("userId", userId)
			.setParameter("readingId", readingId)
			.executeUpdate();
		return deleted > 0;
	}

	private CreatedReadingResponse toResponse(Object[] row) {
		int schemaVersion = ((Number) row[3]).intValue();
		ReadingKind kind = ReadingKind.fromValue((String) row[1]);
		return new CreatedReadingResponse(
			toUuid(row[0]),
			kind,
			(String) row[2],
			schemaVersion,
			(String) row[4],
			(String) row[5],
			fromJson((String) row[6]),
			nullableJson((String) row[7]),
			(String) row[8],
			toInstant(row[9]),
			toInstant(row[10])
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

}
