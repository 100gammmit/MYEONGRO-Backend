package com.myeongro.api.domain.profile.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.atLeastOnce;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import com.myeongro.api.global.auth.oauth.OAuthProviderUserInfo;

class JdbcOAuthAccountRepositoryTests {

	private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
	private final JdbcOAuthAccountRepository repository =
		new JdbcOAuthAccountRepository(jdbcTemplate);

	@Test
	void existingAccountLookupIgnoresWithdrawnProfiles() {
		OAuthProviderUserInfo userInfo = new OAuthProviderUserInfo(
			"kakao",
			"12345",
			"명로 사용자",
			"user@example.com"
		);
		when(jdbcTemplate.query(
			any(String.class),
			any(ResultSetExtractor.class),
			eq("kakao"),
			eq("12345")
		)).thenReturn(null);

		assertThatThrownBy(() -> repository.provision(userInfo))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("OAuth account provisioning failed");

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(jdbcTemplate, atLeastOnce()).query(
			sql.capture(),
			any(ResultSetExtractor.class),
			eq("kakao"),
			eq("12345")
		);
		assertThat(selectAccountQueries(sql)).allMatch(value ->
			value.contains("profiles.deleted_at is null")
		);
	}

	@Test
	void provisioningSerializesByProviderAccountBeforeLookupAndInsert() {
		OAuthProviderUserInfo userInfo = new OAuthProviderUserInfo(
			"kakao",
			"12345",
			"명로 사용자",
			"user@example.com"
		);
		when(jdbcTemplate.query(
			any(String.class),
			any(ResultSetExtractor.class),
			eq("kakao"),
			eq("12345")
		)).thenReturn(null);

		assertThatThrownBy(() -> repository.provision(userInfo))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("OAuth account provisioning failed");

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(jdbcTemplate, atLeastOnce()).query(
			sql.capture(),
			any(ResultSetExtractor.class),
			eq("kakao"),
			eq("12345")
		);
		assertThat(sql.getAllValues().getFirst())
			.contains("pg_advisory_xact_lock")
			.contains("hashtext");
	}

	private List<String> selectAccountQueries(ArgumentCaptor<String> sql) {
		return sql.getAllValues().stream()
			.filter(value -> value.contains("from public.oauth_accounts"))
			.toList();
	}
}
