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

class JdbcGuestReadingCacheRepositoryTests {

	private static final UUID GUEST_SESSION_ID =
		UUID.fromString("9775ff70-5708-45d8-85f8-cb57878bc25d");
	private static final UUID REQUEST_ID =
		UUID.fromString("82ed11d5-2269-438c-9815-42e6f13735f4");
	private static final UUID CACHE_ID =
		UUID.fromString("20e84e95-f5ff-4d9d-a6c4-a3c8ea2e2dfc");

	private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
	private final JdbcGuestReadingCacheRepository repository =
		new JdbcGuestReadingCacheRepository(jdbcTemplate, new ObjectMapper());

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
	void createsPendingGuestCacheThroughDatabaseFunctionBoundary() {
		PendingGuestReadingCache pending = repository.createPending(command());

		assertThat(pending.cacheId()).isEqualTo(CACHE_ID);
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
			.contains("public.create_pending_guest_reading_cache")
			.contains("cast(? as public.reading_kind)")
			.contains("cast(? as jsonb)");
		assertThat(args.getAllValues().get(0)).containsExactly(
			GUEST_SESSION_ID,
			"ip-hash",
			REQUEST_ID,
			"input-hash",
			"tarot",
			"{\"question\":\"How is today?\"}"
		);
		assertThat(sql.getAllValues().get(1))
			.contains("from public.guest_reading_cache")
			.contains("where id = ?");
		assertThat(args.getAllValues().get(1)).containsExactly(CACHE_ID);
	}

	@Test
	void completesPendingGuestCacheThroughDatabaseFunctionBoundary() {
		when(jdbcTemplate.queryForObject(anyString(), eq(Object.class), any(Object[].class)))
			.thenReturn(1);
		PendingGuestReadingCache pending = new PendingGuestReadingCache(CACHE_ID, null);

		var response = repository.completePending(pending, result());

		assertThat(response.id()).isEqualTo(CACHE_ID);

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate)
			.queryForObject(sql.capture(), eq(Object.class), args.capture());
		assertThat(sql.getValue())
			.contains("public.complete_guest_reading_cache")
			.contains("cast(? as jsonb)");
		assertThat(args.getValue()[0]).isEqualTo(CACHE_ID);
		assertThat(args.getValue()[1]).isEqualTo("Completed title");
		assertThat((String) args.getValue()[2])
			.contains("\"title\":\"Completed title\"")
			.contains("\"summary\":\"Summary\"");
	}

	@Test
	void failsPendingGuestCacheThroughDatabaseFunctionBoundary() {
		when(jdbcTemplate.queryForObject(anyString(), eq(Object.class), any(Object[].class)))
			.thenReturn(1);
		PendingGuestReadingCache pending = new PendingGuestReadingCache(CACHE_ID, null);

		repository.failPending(pending, "READING_GENERATION_FAILED");

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate)
			.queryForObject(sql.capture(), eq(Object.class), args.capture());
		assertThat(sql.getValue()).contains("public.fail_guest_reading_cache");
		assertThat(args.getValue()).containsExactly(
			CACHE_ID,
			"READING_GENERATION_FAILED"
		);
	}

	@Test
	void mapsQuotaSqlStateToDomainException() {
		when(jdbcTemplate.queryForObject(
			anyString(),
			org.mockito.ArgumentMatchers.<RowMapper<Object>>any(),
			any(Object[].class)
		)).thenThrow(sqlException("RL102"));

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
			.userId(null)
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
		if (sql.contains("create_pending_guest_reading_cache")) {
			ResultSet resultSet = org.mockito.Mockito.mock(ResultSet.class);
			when(resultSet.getObject("cache_id", UUID.class)).thenReturn(CACHE_ID);
			return resultSet;
		}
		ResultSet resultSet = org.mockito.Mockito.mock(ResultSet.class);
		when(resultSet.getObject("id", UUID.class)).thenReturn(CACHE_ID);
		when(resultSet.getString("kind")).thenReturn("tarot");
		when(resultSet.getString("status")).thenReturn("generating");
		when(resultSet.getString("title")).thenReturn("Generating...");
		when(resultSet.getString("input")).thenReturn("{\"question\":\"How is today?\"}");
		when(resultSet.getString("result")).thenReturn(null);
		when(resultSet.getString("error_code")).thenReturn(null);
		when(resultSet.getTimestamp("created_at"))
			.thenReturn(Timestamp.from(Instant.parse("2026-06-16T00:00:00Z")));
		when(resultSet.getTimestamp("updated_at"))
			.thenReturn(Timestamp.from(Instant.parse("2026-06-16T00:00:01Z")));
		return resultSet;
	}

	private UncategorizedSQLException sqlException(String sqlState) {
		return new UncategorizedSQLException(
			"guest cache boundary",
			"select",
			new SQLException("mapped", sqlState)
		);
	}
}
