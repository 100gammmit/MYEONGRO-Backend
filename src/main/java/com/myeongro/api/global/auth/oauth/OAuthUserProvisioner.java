package com.myeongro.api.global.auth.oauth;

public interface OAuthUserProvisioner {

	ProvisionedOAuthUser provision(OAuthProviderUserInfo userInfo);
}
