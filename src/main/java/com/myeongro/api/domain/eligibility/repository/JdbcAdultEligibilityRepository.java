package com.myeongro.api.domain.eligibility.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAdultEligibilityRepository implements AdultEligibilityRepository {

	private final JdbcTemplate jdbcTemplate;

	public JdbcAdultEligibilityRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public boolean hasConfirmation(UUID userId) {
		Boolean exists = jdbcTemplate.queryForObject(
			"""
			select exists (
				select 1
				from public.adult_eligibility_assertions
				where user_id = ?
			)
			""",
			Boolean.class,
			userId
		);
		return Boolean.TRUE.equals(exists);
	}

	@Override
	public void saveConfirmation(
		UUID userId,
		String policyVersion,
		Instant confirmedAt,
		String method
	) {
		jdbcTemplate.update(
			"""
			insert into public.adult_eligibility_assertions (
				user_id, policy_version, confirmed_at, method
			)
			values (?, ?, ?, ?)
			on conflict (user_id, policy_version) do nothing
			""",
			userId,
			policyVersion,
			Timestamp.from(confirmedAt),
			method
		);
	}
}
