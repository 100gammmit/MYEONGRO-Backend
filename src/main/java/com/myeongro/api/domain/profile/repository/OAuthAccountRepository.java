package com.myeongro.api.domain.profile.repository;

import com.myeongro.api.global.auth.oauth.OAuthProviderUserInfo;
import com.myeongro.api.global.auth.oauth.ProvisionedOAuthUser;

public interface OAuthAccountRepository {

	ProvisionedOAuthUser provision(OAuthProviderUserInfo userInfo);
}
