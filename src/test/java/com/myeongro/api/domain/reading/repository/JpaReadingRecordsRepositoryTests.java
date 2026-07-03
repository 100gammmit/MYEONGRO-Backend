package com.myeongro.api.domain.reading.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.reading.dto.CreatedReadingResponse;

@DataJpaTest
@Import({JpaReadingRecordsRepository.class, ObjectMapper.class})
@TestPropertySource(properties = {
	"spring.jpa.hibernate.ddl-auto=none",
	"spring.flyway.enabled=false",
	"spring.sql.init.mode=always",
	"spring.sql.init.schema-locations=classpath:reading-records-schema.sql"
})
class JpaReadingRecordsRepositoryTests {

	private static final UUID USER_ID =
		UUID.fromString("3b413be2-2b81-4802-8c6a-f868a85d8d83");
	private static final UUID OTHER_USER_ID =
		UUID.fromString("4c524cf3-3c92-4913-9d7b-7c97996e9e94");
	private static final UUID READING_ID =
		UUID.fromString("20e84e95-f5ff-4d9d-a6c4-a3c8ea2e2dfc");
	private static final UUID DELETED_READING_ID =
		UUID.fromString("20e84e95-f5ff-4d9d-a6c4-a3c8ea2e2dff");

	private JdbcTemplate jdbcTemplate;

	@Autowired
	private JpaReadingRecordsRepository repository;

	@Autowired
	void setDataSource(DataSource dataSource) {
		this.jdbcTemplate = new JdbcTemplate(dataSource);
	}

	@Test
	void listsOnlyActiveReadingsOwnedByUserWithLatestGenerationError() {
		insertReading(READING_ID, USER_ID, null, "failed");
		insertReading(DELETED_READING_ID, USER_ID, "2026-06-16T00:00:00Z", "completed");
		insertReading(UUID.randomUUID(), OTHER_USER_ID, null, "completed");
		insertGeneration(READING_ID, "OLD_ERROR", "2026-06-15T00:00:00Z");
		insertGeneration(READING_ID, "LATEST_ERROR", "2026-06-16T00:00:00Z");

		var readings = repository.listByUser(USER_ID);

		assertThat(readings).hasSize(1);
		CreatedReadingResponse reading = readings.getFirst();
		assertThat(reading.id()).isEqualTo(READING_ID);
		assertThat(reading.errorCode()).isEqualTo("LATEST_ERROR");
		assertThat(reading.input()).containsEntry("question", "How is today?");
	}

	@Test
	void softDeletesOnlyActiveReadingOwnedByUser() {
		insertReading(READING_ID, USER_ID, null, "completed");

		boolean deleted = repository.softDeleteByUserAndId(USER_ID, READING_ID);
		boolean secondDelete = repository.softDeleteByUserAndId(USER_ID, READING_ID);

		assertThat(deleted).isTrue();
		assertThat(secondDelete).isFalse();
		assertThat(repository.findByUserAndId(USER_ID, READING_ID)).isEmpty();
	}

	private void insertReading(
		UUID readingId,
		UUID userId,
		String deletedAt,
		String status
	) {
		jdbcTemplate.update(
			"""
			insert into public.readings (
				id, user_id, kind, status, title, input, result,
				deleted_at, created_at, updated_at
			)
			values (?, ?, 'tarot', ?, 'A title', ?, ?, ?, ?, ?)
			""",
			readingId,
			userId,
			status,
			"{\"question\":\"How is today?\"}",
			"{\"title\":\"A title\"}",
			deletedAt == null ? null : java.sql.Timestamp.from(Instant.parse(deletedAt)),
			java.sql.Timestamp.from(Instant.parse("2026-06-15T00:00:00Z")),
			java.sql.Timestamp.from(Instant.parse("2026-06-15T00:00:00Z"))
		);
	}

	private void insertGeneration(UUID readingId, String errorCode, String createdAt) {
		jdbcTemplate.update(
			"""
			insert into public.generation_records (reading_id, error_code, created_at)
			values (?, ?, ?)
			""",
			readingId,
			errorCode,
			java.sql.Timestamp.from(Instant.parse(createdAt))
		);
	}
}
