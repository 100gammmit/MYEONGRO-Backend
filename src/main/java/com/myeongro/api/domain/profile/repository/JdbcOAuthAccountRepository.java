package com.myeongro.api.domain.profile.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.myeongro.api.global.auth.oauth.OAuthProviderUserInfo;
import com.myeongro.api.global.auth.oauth.ProvisionedOAuthUser;

@Repository
public class JdbcOAuthAccountRepository implements OAuthAccountRepository {

	private static final String SELECT_ACCOUNT = """
		select
			profiles.id as user_id,
			coalesce(profiles.display_name, oauth_accounts.display_name) as display_name,
			oauth_accounts.provider,
			oauth_accounts.provider_user_id
		from public.oauth_accounts
		join public.profiles on profiles.id = oauth_accounts.profile_id
		where oauth_accounts.provider = ?
			and oauth_accounts.provider_user_id = ?
			and profiles.deleted_at is null
		""";
	private static final String INSERT_PROFILE = """
		insert into public.profiles (id, display_name)
		values (?, ?)
		on conflict (id) do update
		set display_name = coalesce(public.profiles.display_name, excluded.display_name)
		""";
	private static final String INSERT_ACCOUNT = """
		insert into public.oauth_accounts (
			profile_id,
			provider,
			provider_user_id,
			email,
			display_name
		)
		values (?, ?, ?, ?, ?)
		on conflict (provider, provider_user_id) do update
		set email = excluded.email,
			display_name = excluded.display_name,
			updated_at = now()
		""";

	private final JdbcTemplate jdbcTemplate;

	public JdbcOAuthAccountRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	@Transactional
	public ProvisionedOAuthUser provision(OAuthProviderUserInfo userInfo) {
		ProvisionedOAuthUser existing = findExisting(userInfo);
		if (existing != null) {
			return existing;
		}

		UUID userId = UUID.randomUUID();
		jdbcTemplate.update(INSERT_PROFILE, userId, userInfo.displayName());
		jdbcTemplate.update(
			INSERT_ACCOUNT,
			userId,
			userInfo.provider(),
			userInfo.providerUserId(),
			userInfo.email(),
			userInfo.displayName()
		);
		ProvisionedOAuthUser provisioned = findExisting(userInfo);
		if (provisioned == null) {
			throw new IllegalStateException("OAuth account provisioning failed");
		}
		return provisioned;
	}

	private ProvisionedOAuthUser findExisting(OAuthProviderUserInfo userInfo) {
		return jdbcTemplate.query(
			SELECT_ACCOUNT,
			resultSet -> resultSet.next() ? toUser(resultSet) : null,
			userInfo.provider(),
			userInfo.providerUserId()
		);
	}

	private ProvisionedOAuthUser toUser(ResultSet resultSet) throws SQLException {
		return new ProvisionedOAuthUser(
			resultSet.getObject("user_id", UUID.class),
			resultSet.getString("display_name"),
			resultSet.getString("provider"),
			resultSet.getString("provider_user_id")
		);
	}

}
