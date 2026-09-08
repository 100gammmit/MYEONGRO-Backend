package com.myeongro.api.domain.profile.repository;

import static org.assertj.core.api.Assertions.assertThat;

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
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private JpaAccountWithdrawalRepository repository;

	@Autowired
	void setDataSource(DataSource dataSource) {
		this.jdbcTemplate = new JdbcTemplate(dataSource);
	}

	@Test
	void withdrawPermanentlyDeletesProfileAndCascadesAccountData() {
		insertProfile(USER_ID, "Leaving user");
		insertOAuthAccount(USER_ID);
		insertReading(READING_ID, USER_ID);
		insertGenerationRecord(READING_ID);
		insertConsentEvent(USER_ID);

		repository.deletePermanently(USER_ID);

		assertThat(countOAuthAccounts(USER_ID)).isZero();
		assertThat(countReadings(READING_ID)).isZero();
		assertThat(countRows("generation_records")).isZero();
		assertThat(countRows("consent_events")).isZero();
		assertThat(countProfiles(USER_ID)).isZero();
	}

	@Test
	void deletingAMissingAccountIsIdempotent() {
		repository.deletePermanently(USER_ID);

		assertThat(countProfiles(USER_ID)).isZero();
	}

	private void insertProfile(UUID userId, String displayName) {
		jdbcTemplate.update(
			"""
			insert into public.profiles (id, display_name)
			values (?, ?)
			""",
			userId,
			displayName
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

	private void insertReading(UUID readingId, UUID userId) {
		jdbcTemplate.update(
			"""
			insert into public.readings (id, user_id)
			values (?, ?)
			""",
			readingId,
			userId
		);
	}

	private Integer countOAuthAccounts(UUID userId) {
		return jdbcTemplate.queryForObject(
			"select count(*) from public.oauth_accounts where profile_id = ?",
			Integer.class,
			userId
		);
	}

	private Integer countReadings(UUID readingId) {
		return jdbcTemplate.queryForObject(
			"select count(*) from public.readings where id = ?",
			Integer.class,
			readingId
		);
	}

	private void insertGenerationRecord(UUID readingId) {
		jdbcTemplate.update(
			"insert into public.generation_records (reading_id) values (?)",
			readingId
		);
	}

	private void insertConsentEvent(UUID userId) {
		jdbcTemplate.update(
			"""
			insert into public.consent_events (user_id, document_type)
			values (?, 'TERMS')
			""",
			userId
		);
	}

	private Integer countProfiles(UUID userId) {
		return jdbcTemplate.queryForObject(
			"select count(*) from public.profiles where id = ?",
			Integer.class,
			userId
		);
	}

	private Integer countRows(String tableName) {
		return jdbcTemplate.queryForObject(
			"select count(*) from public." + tableName,
			Integer.class
		);
	}
}
