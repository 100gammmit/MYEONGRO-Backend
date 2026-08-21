package com.myeongro.api.domain.reading.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.reading.exception.InsufficientReadingCreditsException;
import com.myeongro.api.domain.reading.exception.ReadingGenerationInProgressException;
import com.myeongro.api.domain.reading.service.ReadingGenerationMetadata;

class JpaReadingRecordsRepositoryCreditTests {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final UUID READING_ID = UUID.randomUUID();
	private static final ReadingGenerationMetadata METADATA =
		new ReadingGenerationMetadata("openai", "model", "prompt");

	@Test
	void mapsActiveGenerationConflictDuringRetry() {
		var repository = repositoryThrowing("RL111");

		assertThatThrownBy(() -> repository.startFailedRetry(
			USER_ID, READING_ID, METADATA, 3, 10
		)).isInstanceOf(ReadingGenerationInProgressException.class);
	}

	@Test
	void mapsInsufficientCreditsWithCurrentRetryCost() {
		var repository = repositoryThrowing("RL112");

		assertThatThrownBy(() -> repository.startFailedRetry(
			USER_ID, READING_ID, METADATA, 3, 10
		)).isInstanceOfSatisfying(
			InsufficientReadingCreditsException.class,
			exception -> {
				assertThat(exception.getUserId()).isEqualTo(USER_ID);
				assertThat(exception.getRequired()).isEqualTo(3);
			}
		);
	}

	private JpaReadingRecordsRepository repositoryThrowing(String sqlState) {
		JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
		SQLException sqlException = new SQLException("failure", sqlState);
		when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
			.thenThrow(new DataAccessResourceFailureException("failure", sqlException));
		return new JpaReadingRecordsRepository(jdbcTemplate, new ObjectMapper());
	}
}
