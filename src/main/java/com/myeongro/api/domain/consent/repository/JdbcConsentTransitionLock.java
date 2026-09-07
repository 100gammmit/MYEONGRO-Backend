package com.myeongro.api.domain.consent.repository;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.myeongro.api.domain.consent.entity.ConsentDocumentType;

@Component
public class JdbcConsentTransitionLock implements ConsentTransitionLock {

	private static final String LOCK_TRANSITION = """
		select pg_advisory_xact_lock(
			hashtextextended('consent:' || cast(? as text) || ':' || ?, 0)
		)
		""";

	private final JdbcTemplate jdbcTemplate;

	public JdbcConsentTransitionLock(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void lock(UUID userId, ConsentDocumentType documentType) {
		jdbcTemplate.query(
			LOCK_TRANSITION,
			resultSet -> null,
			userId,
			documentType.name()
		);
	}
}
