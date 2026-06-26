package com.myeongro.api.global.auth.oauth;

public record OAuthProviderUserInfo(
	String provider,
	String providerUserId,
	String displayName,
	String email
) {
}
