package com.myeongro.api.domain.profile.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.myeongro.api.global.auth.oauth.OAuthProviderUserInfo;

@Component
public class JdbcOAuthAccountLock implements OAuthAccountLock {

	private static final String LOCK_ACCOUNT = """
		select pg_advisory_xact_lock(hashtext(?), hashtext(?))
		""";

	private final JdbcTemplate jdbcTemplate;

	public JdbcOAuthAccountLock(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void lock(OAuthProviderUserInfo userInfo) {
		jdbcTemplate.query(
			LOCK_ACCOUNT,
			resultSet -> null,
			userInfo.provider(),
			userInfo.providerUserId()
		);
	}
}
