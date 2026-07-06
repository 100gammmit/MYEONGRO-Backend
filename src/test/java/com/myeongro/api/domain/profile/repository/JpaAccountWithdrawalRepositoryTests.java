package com.myeongro.api.domain.profile.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@Import(JpaAccountWithdrawalRepository.class)
@TestPropertySource(properties = {
	"spring.jpa.hibernate.ddl-auto=none",
	"spring.flyway.enabled=false",
	"spring.sql.init.mode=always",
	"spring.sql.init.schema-locations=classpath:profile-schema.sql"
})
class JpaAccountWithdrawalRepositoryTests {

	private static final UUID USER_ID =
		UUID.fromString("72e5cd7e-115d-432c-bfc2-1e9d3833a61d");
	private static final UUID READING_ID =
		UUID.fromString("a44f8964-5351-4a77-9b52-bc84d085f215");
	private static final Instant PURGE_AFTER =
		Instant.parse("2026-07-29T00:00:00Z");

	private JdbcTemplate jdbcTemplate;

	@Autowired
	private JpaAccountWithdrawalRepository repository;

	@Autowired
	void setDataSource(DataSource dataSource) {
		this.jdbcTemplate = new JdbcTemplate(dataSource);
	}

	@Test
	void withdrawSoftDeletesProfileAndReadingsAndDeletesOauthAccounts() {
		insertProfile(USER_ID, "Leaving user", null, null);
		insertOAuthAccount(USER_ID);
		insertReading(READING_ID, USER_ID, null);

		repository.withdraw(USER_ID, PURGE_AFTER);

		assertThat(countOAuthAccounts(USER_ID)).isZero();
		assertThat(readingDeletedAt(READING_ID)).isNotNull();
		assertThat(profileDeletedAt(USER_ID)).isNotNull();
		assertThat(profilePurgeAfter(USER_ID)).isEqualTo(PURGE_AFTER);
	}

	@Test
	void withdrawPreservesExistingPurgeDeadline() {
		Instant existingPurgeAfter = Instant.parse("2026-07-20T00:00:00Z");
		insertProfile(USER_ID, "Leaving user", null, existingPurgeAfter);

		repository.withdraw(USER_ID, PURGE_AFTER);

		assertThat(profilePurgeAfter(USER_ID)).isEqualTo(existingPurgeAfter);
	}

	private void insertProfile(
		UUID userId,
		String displayName,
		String deletedAt,
		Instant purgeAfter
	) {
		jdbcTemplate.update(
			"""
			insert into public.profiles (id, display_name, deleted_at, purge_after)
			values (?, ?, ?, ?)
			""",
			userId,
			displayName,
			deletedAt == null ? null : OffsetDateTime.parse(deletedAt),
			purgeAfter == null ? null : OffsetDateTime.parse(purgeAfter.toString())
		);
	}

	private void insertOAuthAccount(UUID userId) {
		jdbcTemplate.update(
			"""
			insert into public.oauth_accounts (profile_id, provider, provider_user_id)
			values (?, 'kakao', 'account-1')
			""",
			userId
		);
	}

	private void insertReading(UUID readingId, UUID userId, String deletedAt) {
		jdbcTemplate.update(
			"""
			insert into public.readings (id, user_id, deleted_at)
			values (?, ?, ?)
			""",
			readingId,
			userId,
			deletedAt == null ? null : OffsetDateTime.parse(deletedAt)
		);
	}

	private Integer countOAuthAccounts(UUID userId) {
		return jdbcTemplate.queryForObject(
			"select count(*) from public.oauth_accounts where profile_id = ?",
			Integer.class,
			userId
		);
	}

	private Instant readingDeletedAt(UUID readingId) {
		return jdbcTemplate.queryForObject(
			"select deleted_at from public.readings where id = ?",
			(resultSet, rowNumber) -> resultSet.getTimestamp(1).toInstant(),
			readingId
		);
	}

	private Instant profileDeletedAt(UUID userId) {
		return jdbcTemplate.queryForObject(
			"select deleted_at from public.profiles where id = ?",
			(resultSet, rowNumber) -> resultSet.getTimestamp(1).toInstant(),
			userId
		);
	}

	private Instant profilePurgeAfter(UUID userId) {
		return jdbcTemplate.queryForObject(
			"select purge_after from public.profiles where id = ?",
			(resultSet, rowNumber) -> resultSet.getTimestamp(1).toInstant(),
			userId
		);
	}
}
