package com.myeongro.api.global.auth.oauth;

public interface PendingSignupPrincipal {

	String provider();

	String providerUserId();

	String accessToken();

	default OAuthProviderUserInfo userInfo() {
		return new OAuthProviderUserInfo(
			provider(),
			providerUserId()
		);
	}
}
