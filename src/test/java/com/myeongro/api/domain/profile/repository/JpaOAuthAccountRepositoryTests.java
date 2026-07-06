package com.myeongro.api.domain.profile.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.myeongro.api.global.auth.oauth.OAuthProviderUserInfo;

@DataJpaTest
@Import({
	JpaOAuthAccountRepository.class,
	JpaOAuthAccountRepositoryTests.TestLocks.class
})
@TestPropertySource(properties = {
	"spring.jpa.hibernate.ddl-auto=none",
	"spring.flyway.enabled=false",
	"spring.sql.init.mode=always",
	"spring.sql.init.schema-locations=classpath:profile-schema.sql"
})
class JpaOAuthAccountRepositoryTests {

	private static final UUID ACTIVE_USER_ID =
		UUID.fromString("4c52fa6b-49b5-4c22-a0a4-48b984d64f32");
	private static final UUID DELETED_USER_ID =
		UUID.fromString("746d837d-8605-4a9c-ad9e-6ffd693c5087");

	private JdbcTemplate jdbcTemplate;

	@Autowired
	private JpaOAuthAccountRepository repository;

	@Autowired
	void setDataSource(DataSource dataSource) {
		this.jdbcTemplate = new JdbcTemplate(dataSource);
	}

	@Test
	void returnsExistingActiveOauthAccount() {
		insertProfile(ACTIVE_USER_ID, "Stored name", null);
		insertOAuthAccount(ACTIVE_USER_ID, "kakao", "12345", "stored@example.com", "Provider name");

		var user = repository.provision(new OAuthProviderUserInfo(
			"kakao",
			"12345",
			"Incoming name",
			"incoming@example.com"
		));

		assertThat(user.userId()).isEqualTo(ACTIVE_USER_ID);
		assertThat(user.displayName()).isEqualTo("Stored name");
		assertThat(user.provider()).isEqualTo("kakao");
		assertThat(user.providerUserId()).isEqualTo("12345");
		assertThat(countProfiles()).isEqualTo(1);
	}

	@Test
	void ignoresWithdrawnProfilesAndCreatesNewProfileAccount() {
		insertProfile(DELETED_USER_ID, "Withdrawn", "2026-07-01T00:00:00Z");

		var user = repository.provision(new OAuthProviderUserInfo(
			"google",
			"abcde",
			"New user",
			"new@example.com"
		));

		assertThat(user.userId()).isNotEqualTo(DELETED_USER_ID);
		assertThat(user.displayName()).isEqualTo("New user");
		assertThat(user.provider()).isEqualTo("google");
		assertThat(user.providerUserId()).isEqualTo("abcde");
		assertThat(countProfiles()).isEqualTo(2);
		assertThat(countOAuthAccountsFor(user.userId())).isEqualTo(1);
	}

	private void insertProfile(UUID userId, String displayName, String deletedAt) {
		jdbcTemplate.update(
			"""
			insert into public.profiles (id, display_name, deleted_at)
			values (?, ?, ?)
			""",
			userId,
			displayName,
			deletedAt == null ? null : OffsetDateTime.parse(deletedAt)
		);
	}

	private void insertOAuthAccount(
		UUID profileId,
		String provider,
		String providerUserId,
		String email,
		String displayName
	) {
		jdbcTemplate.update(
			"""
			insert into public.oauth_accounts (
				profile_id, provider, provider_user_id, email, display_name
			)
			values (?, ?, ?, ?, ?)
			""",
			profileId,
			provider,
			providerUserId,
			email,
			displayName
		);
	}

	private Integer countProfiles() {
		return jdbcTemplate.queryForObject("select count(*) from public.profiles", Integer.class);
	}

	private Integer countOAuthAccountsFor(UUID profileId) {
		return jdbcTemplate.queryForObject(
			"select count(*) from public.oauth_accounts where profile_id = ?",
			Integer.class,
			profileId
		);
	}

	@TestConfiguration
	static class TestLocks {

		@Bean
		OAuthAccountLock oauthAccountLock() {
			return userInfo -> {
			};
		}
	}
}
