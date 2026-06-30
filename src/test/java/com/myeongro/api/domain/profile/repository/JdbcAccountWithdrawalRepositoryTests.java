package com.myeongro.api.domain.profile.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcAccountWithdrawalRepositoryTests {

	private static final UUID USER_ID =
		UUID.fromString("43bc72f9-eed1-4e4b-8717-6fe969b4ea43");
	private static final Instant PURGE_AFTER =
		Instant.parse("2026-07-29T00:00:00Z");

	private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
	private final JdbcAccountWithdrawalRepository repository =
		new JdbcAccountWithdrawalRepository(jdbcTemplate);

	@Test
	void withdrawalSetsProfilePurgeDeadline() {
		repository.withdraw(USER_ID, PURGE_AFTER);

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(jdbcTemplate).update(
			sql.capture(),
			org.mockito.Mockito.eq(Timestamp.from(PURGE_AFTER)),
			org.mockito.Mockito.eq(USER_ID)
		);

		assertThat(sql.getValue())
			.contains("purge_after = coalesce(purge_after, ?)")
			.contains("deleted_at = coalesce(deleted_at, now())");
	}
}
