package com.myeongro.api.domain.eligibility.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@JdbcTest
@Import(JdbcAdultEligibilityRepository.class)
@TestPropertySource(properties = {
	"spring.sql.init.mode=always",
	"spring.sql.init.schema-locations=classpath:profile-schema.sql"
})
class JdbcAdultEligibilityRepositoryTests {

	private static final UUID USER_ID =
		UUID.fromString("43bc72f9-eed1-4e4b-8717-6fe969b4ea43");

	@Autowired
	private JdbcAdultEligibilityRepository repository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void findsAnyRecordedSignupConfirmationRegardlessOfItsPolicyVersion() {
		insertProfile();
		assertThat(repository.hasConfirmation(USER_ID)).isFalse();

		jdbcTemplate.update(
			"""
			insert into public.adult_eligibility_assertions (
				user_id, policy_version, confirmed_at, method
			)
			values (?, ?, ?, ?)
			""",
			USER_ID,
			"2026-09-09",
			Instant.parse("2026-09-13T00:00:00Z"),
			"oauth-signup-self-declaration"
		);

		assertThat(repository.hasConfirmation(USER_ID)).isTrue();
	}

	private void insertProfile() {
		jdbcTemplate.update(
			"insert into public.profiles (id, display_name) values (?, ?)",
			USER_ID,
			"명로 사용자"
		);
	}
}
