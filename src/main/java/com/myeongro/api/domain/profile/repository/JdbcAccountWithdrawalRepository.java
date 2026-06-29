package com.myeongro.api.domain.profile.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcAccountWithdrawalRepository implements AccountWithdrawalRepository {

	private static final String SOFT_DELETE_READINGS = """
		update public.readings
		set deleted_at = now()
		where user_id = ?
		  and deleted_at is null
		""";
	private static final String DELETE_OAUTH_ACCOUNTS = """
		delete from public.oauth_accounts
		where profile_id = ?
		""";
	private static final String SOFT_DELETE_PROFILE = """
		update public.profiles
		set deleted_at = coalesce(deleted_at, now()),
			purge_after = coalesce(purge_after, ?),
			updated_at = now()
		where id = ?
		""";

	private final JdbcTemplate jdbcTemplate;

	public JdbcAccountWithdrawalRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	@Transactional
	public void withdraw(UUID userId, Instant purgeAfter) {
		jdbcTemplate.update(SOFT_DELETE_READINGS, userId);
		jdbcTemplate.update(DELETE_OAUTH_ACCOUNTS, userId);
		jdbcTemplate.update(SOFT_DELETE_PROFILE, purgeAfter, userId);
	}
}
