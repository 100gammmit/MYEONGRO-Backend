package com.myeongro.api.domain.consent.repository;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import com.myeongro.api.domain.consent.entity.ConsentDocumentType;

class JdbcConsentTransitionLockTests {

	@Test
	void locksByUserAndDocumentForTheCurrentTransaction() {
		JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
		UUID userId = UUID.randomUUID();
		var lock = new JdbcConsentTransitionLock(jdbcTemplate);

		lock.lock(userId, ConsentDocumentType.AI_OVERSEAS_TRANSFER);

		verify(jdbcTemplate).query(
			org.mockito.ArgumentMatchers.contains("pg_advisory_xact_lock"),
			org.mockito.ArgumentMatchers.<ResultSetExtractor<Object>>any(),
			eq(userId),
			eq("AI_OVERSEAS_TRANSFER")
		);
	}
}
