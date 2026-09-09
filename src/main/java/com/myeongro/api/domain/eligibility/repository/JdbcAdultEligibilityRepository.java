package com.myeongro.api.domain.eligibility.repository;

import java.time.Instant;
import java.sql.Timestamp;
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
