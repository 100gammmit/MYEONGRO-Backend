package com.myeongro.api.domain.readingcredit.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JdbcReadingCreditRepositoryTests {

	@Test
	void readsLazyResetStatusThroughDatabaseFunction() throws Exception {
		JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
		ResultSet resultSet = org.mockito.Mockito.mock(ResultSet.class);
		when(resultSet.getInt("free_balance")).thenReturn(7);
		when(resultSet.getInt("paid_balance")).thenReturn(5);
		when(resultSet.getBoolean("generation_in_progress")).thenReturn(true);
		when(jdbcTemplate.queryForObject(
			anyString(),
			org.mockito.ArgumentMatchers.<RowMapper<ReadingCreditSnapshot>>any(),
			any(Object[].class)
		)).thenAnswer(invocation -> {
			RowMapper<ReadingCreditSnapshot> mapper = invocation.getArgument(1);
			return mapper.mapRow(resultSet, 0);
		});
		var repository = new JdbcReadingCreditRepository(jdbcTemplate);
		UUID userId = UUID.randomUUID();

		var snapshot = repository.getStatus(userId, 10);

		assertThat(snapshot).isEqualTo(new ReadingCreditSnapshot(7, 5, true));
		ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate).queryForObject(
			org.mockito.ArgumentMatchers.contains("get_reading_credit_status"),
			org.mockito.ArgumentMatchers.<RowMapper<ReadingCreditSnapshot>>any(),
			args.capture()
		);
		assertThat(args.getValue()).containsExactly(userId, 10);
	}

	@Test
	void passesStaleDurationAsPostgresInterval() {
		JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
		when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
			.thenReturn(3);
		var repository = new JdbcReadingCreditRepository(jdbcTemplate);

		assertThat(repository.failStaleGenerations(Duration.ofMinutes(5))).isEqualTo(3);

		ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate).queryForObject(
			org.mockito.ArgumentMatchers.contains("fail_stale_reading_generations"),
			eq(Integer.class),
			args.capture()
		);
		assertThat(args.getValue()).containsExactly("300 seconds");
	}
}
