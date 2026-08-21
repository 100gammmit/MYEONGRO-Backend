package com.myeongro.api.domain.readingcredit.repository;

import java.time.Duration;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcReadingCreditRepository implements ReadingCreditRepository {

	private static final String GET_STATUS = """
		select free_balance, paid_balance, generation_in_progress
		from public.get_reading_credit_status(?, ?)
		""";
	private static final String FAIL_STALE = """
		select public.fail_stale_reading_generations(cast(? as interval))
		""";

	private final JdbcTemplate jdbcTemplate;

	public JdbcReadingCreditRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public ReadingCreditSnapshot getStatus(UUID userId, int dailyFreeGrant) {
		return jdbcTemplate.queryForObject(
			GET_STATUS,
			(resultSet, rowNumber) -> new ReadingCreditSnapshot(
				resultSet.getInt("free_balance"),
				resultSet.getInt("paid_balance"),
				resultSet.getBoolean("generation_in_progress")
			),
			userId,
			dailyFreeGrant
		);
	}

	@Override
	public int failStaleGenerations(Duration staleAfter) {
		Integer count = jdbcTemplate.queryForObject(
			FAIL_STALE,
			Integer.class,
			staleAfter.toSeconds() + " seconds"
		);
		return count == null ? 0 : count;
	}
}
