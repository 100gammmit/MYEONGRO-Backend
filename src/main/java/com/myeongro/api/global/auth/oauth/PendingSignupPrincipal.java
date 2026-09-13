package com.myeongro.api.global.auth.oauth;

public interface PendingSignupPrincipal {

	String provider();

	String providerUserId();

	String displayName();

	String email();

	String accessToken();

	default OAuthProviderUserInfo userInfo() {
		return new OAuthProviderUserInfo(
			provider(),
			providerUserId(),
			displayName(),
			email()
		);
	}
}
