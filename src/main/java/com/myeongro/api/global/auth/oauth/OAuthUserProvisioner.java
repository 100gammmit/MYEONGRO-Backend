package com.myeongro.api.global.auth.oauth;

import java.util.Optional;

public interface OAuthUserProvisioner {

	Optional<ProvisionedOAuthUser> findExisting(OAuthProviderUserInfo userInfo);

	ProvisionedOAuthUser provision(OAuthProviderUserInfo userInfo);
}
