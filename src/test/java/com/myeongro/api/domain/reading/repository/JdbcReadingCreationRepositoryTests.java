package com.myeongro.api.domain.reading.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.reading.dto.ReadingResult;
import com.myeongro.api.domain.reading.dto.ReadingSection;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.exception.FreeReadingIdempotencyConflictException;
import com.myeongro.api.domain.reading.exception.FreeReadingQuotaExceededException;

class JdbcReadingCreationRepositoryTests {

	private static final UUID USER_ID =
		UUID.fromString("3b413be2-2b81-4802-8c6a-f868a85d8d83");
	private static final UUID GUEST_SESSION_ID =
		UUID.fromString("9775ff70-5708-45d8-85f8-cb57878bc25d");
	private static final UUID REQUEST_ID =
		UUID.fromString("82ed11d5-2269-438c-9815-42e6f13735f4");
	private static final UUID READING_ID =
		UUID.fromString("20e84e95-f5ff-4d9d-a6c4-a3c8ea2e2dfc");
	private static final Long GENERATION_ID = 42L;

	private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
	private final JdbcReadingCreationRepository repository =
		new JdbcReadingCreationRepository(jdbcTemplate, new ObjectMapper());

	@BeforeEach
	void setUp() {
		when(jdbcTemplate.queryForObject(
			anyString(),
			org.mockito.ArgumentMatchers.<RowMapper<Object>>any(),
			any(Object[].class)
		)).thenAnswer(invocation -> {
			String sql = invocation.getArgument(0);
			RowMapper<?> rowMapper = invocation.getArgument(1);
			return rowMapper.mapRow(resultSetFor(sql), 0);
		});
	}

	@Test
	void createsPendingUserReadingThroughDatabaseFunctionBoundary() {
		PendingReadingCreation pending = repository.createPending(command());

		assertThat(pending.readingId()).isEqualTo(READING_ID);
		assertThat(pending.generationId()).isEqualTo(GENERATION_ID);
		assertThat(pending.reading().status()).isEqualTo("generating");
		assertThat(pending.reading().input()).containsEntry("question", "How is today?");

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate, org.mockito.Mockito.times(2))
			.queryForObject(
				sql.capture(),
				org.mockito.ArgumentMatchers.<RowMapper<Object>>any(),
				args.capture()
			);

		assertThat(sql.getAllValues().get(0))
			.contains("public.create_pending_free_reading")
			.contains("cast(? as public.reading_kind)")
			.contains("cast(? as jsonb)");
		assertThat(args.getAllValues().get(0)).containsExactly(
			USER_ID,
			GUEST_SESSION_ID,
			"ip-hash",
			REQUEST_ID,
			"input-hash",
			"tarot",
			"{\"question\":\"How is today?\"}",
			"openai",
			"gpt-test",
			"prompt-v1"
		);
		assertThat(sql.getAllValues().get(1))
			.contains("from public.readings")
			.contains("where id = ?");
		assertThat(args.getAllValues().get(1)).containsExactly(READING_ID);
	}

	@Test
	void completesPendingReadingThroughDatabaseFunctionBoundary() {
		when(jdbcTemplate.queryForObject(anyString(), eq(Object.class), any(Object[].class)))
			.thenReturn(1);
		PendingReadingCreation pending = new PendingReadingCreation(
			READING_ID,
			GENERATION_ID,
			null
		);

		var response = repository.completePending(pending, result());

		assertThat(response.id()).isEqualTo(READING_ID);

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate)
			.queryForObject(sql.capture(), eq(Object.class), args.capture());
		assertThat(sql.getValue())
			.contains("public.complete_free_reading_generation")
			.contains("cast(? as jsonb)");
		assertThat(args.getValue()[0]).isEqualTo(READING_ID);
		assertThat(args.getValue()[1]).isEqualTo(GENERATION_ID);
		assertThat(args.getValue()[2]).isEqualTo("Completed title");
		assertThat((String) args.getValue()[3])
			.contains("\"title\":\"Completed title\"")
			.contains("\"summary\":\"Summary\"");
	}

	@Test
	void failsPendingReadingThroughDatabaseFunctionBoundary() {
		when(jdbcTemplate.queryForObject(anyString(), eq(Object.class), any(Object[].class)))
			.thenReturn(1);
		PendingReadingCreation pending = new PendingReadingCreation(
			READING_ID,
			GENERATION_ID,
			null
		);

		repository.failPending(pending, "READING_GENERATION_FAILED");

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate)
			.queryForObject(sql.capture(), eq(Object.class), args.capture());
		assertThat(sql.getValue()).contains("public.fail_free_reading_generation");
		assertThat(args.getValue()).containsExactly(
			READING_ID,
			GENERATION_ID,
			"READING_GENERATION_FAILED"
		);
	}

	@Test
	void mapsQuotaSqlStateToDomainException() {
		when(jdbcTemplate.queryForObject(
			anyString(),
			org.mockito.ArgumentMatchers.<RowMapper<Object>>any(),
			any(Object[].class)
		)).thenThrow(sqlException("RL101"));

		assertThatThrownBy(() -> repository.createPending(command()))
			.isInstanceOf(FreeReadingQuotaExceededException.class);
	}

	@Test
	void mapsIdempotencySqlStateToDomainException() {
		when(jdbcTemplate.queryForObject(
			anyString(),
			org.mockito.ArgumentMatchers.<RowMapper<Object>>any(),
			any(Object[].class)
		)).thenThrow(sqlException("RL104"));

		assertThatThrownBy(() -> repository.createPending(command()))
			.isInstanceOf(FreeReadingIdempotencyConflictException.class);
	}

	private PendingReadingCommand command() {
		return PendingReadingCommand.builder()
			.userId(USER_ID)
			.guestSessionId(GUEST_SESSION_ID)
			.ipHash("ip-hash")
			.requestId(REQUEST_ID)
			.inputHash("input-hash")
			.kind(ReadingKind.TAROT)
			.input(Map.of("question", "How is today?"))
			.provider("openai")
			.model("gpt-test")
			.promptVersion("prompt-v1")
			.build();
	}

	private ReadingResult result() {
		return new ReadingResult(
			"Completed title",
			"Summary",
			List.of(new ReadingSection("Heading", "Body")),
			List.of("Guidance"),
			"Disclaimer"
		);
	}

	private ResultSet resultSetFor(String sql) throws SQLException {
		if (sql.contains("create_pending_free_reading")) {
			ResultSet resultSet = org.mockito.Mockito.mock(ResultSet.class);
			when(resultSet.getObject("reading_id", UUID.class)).thenReturn(READING_ID);
			when(resultSet.getObject("generation_id", Long.class)).thenReturn(GENERATION_ID);
			return resultSet;
		}
		ResultSet resultSet = org.mockito.Mockito.mock(ResultSet.class);
		when(resultSet.getObject("id", UUID.class)).thenReturn(READING_ID);
		when(resultSet.getString("kind")).thenReturn("tarot");
		when(resultSet.getString("status")).thenReturn("generating");
		when(resultSet.getString("title")).thenReturn("Generating...");
		when(resultSet.getString("input")).thenReturn("{\"question\":\"How is today?\"}");
		when(resultSet.getString("result")).thenReturn(null);
		when(resultSet.getTimestamp("created_at"))
			.thenReturn(Timestamp.from(Instant.parse("2026-06-16T00:00:00Z")));
		when(resultSet.getTimestamp("updated_at"))
			.thenReturn(Timestamp.from(Instant.parse("2026-06-16T00:00:01Z")));
		return resultSet;
	}

	private UncategorizedSQLException sqlException(String sqlState) {
		return new UncategorizedSQLException(
			"reading boundary",
			"select",
			new SQLException("mapped", sqlState)
		);
	}
}
