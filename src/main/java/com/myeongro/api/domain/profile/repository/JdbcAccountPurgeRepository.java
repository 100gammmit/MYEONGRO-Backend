package com.myeongro.api.domain.profile.repository;

import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcAccountPurgeRepository implements AccountPurgeRepository {

	private static final String DELETE_DUE_READINGS = """
		delete from public.readings
		where user_id in (
			select id
			from public.profiles
			where deleted_at is not null
			  and purge_after <= ?
			  and purged_at is null
			order by purge_after
			limit ?
		)
		""";
	private static final String DELETE_DUE_OAUTH_ACCOUNTS = """
		delete from public.oauth_accounts
		where profile_id in (
			select id
			from public.profiles
			where deleted_at is not null
			  and purge_after <= ?
			  and purged_at is null
			order by purge_after
			limit ?
		)
		""";
	private static final String PURGE_DUE_PROFILES = """
		update public.profiles
		set display_name = null,
			purged_at = coalesce(purged_at, ?),
			updated_at = now()
		where id in (
			select id
			from public.profiles
			where deleted_at is not null
			  and purge_after <= ?
			  and purged_at is null
			order by purge_after
			limit ?
		)
		""";

	private final JdbcTemplate jdbcTemplate;

	public JdbcAccountPurgeRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	@Transactional
	public int purgeDueProfiles(Instant now, int batchSize) {
		jdbcTemplate.update(DELETE_DUE_READINGS, now, batchSize);
		jdbcTemplate.update(DELETE_DUE_OAUTH_ACCOUNTS, now, batchSize);
		return jdbcTemplate.update(PURGE_DUE_PROFILES, now, now, batchSize);
	}
}
