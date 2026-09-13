package com.myeongro.api.domain.profile.repository;

import java.util.Optional;

import com.myeongro.api.global.auth.oauth.OAuthProviderUserInfo;
import com.myeongro.api.global.auth.oauth.ProvisionedOAuthUser;

public interface OAuthAccountRepository {

	Optional<ProvisionedOAuthUser> findExisting(OAuthProviderUserInfo userInfo);

	ProvisionedOAuthUser provision(OAuthProviderUserInfo userInfo);
}
