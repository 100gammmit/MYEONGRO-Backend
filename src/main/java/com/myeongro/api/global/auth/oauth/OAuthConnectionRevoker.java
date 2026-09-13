package com.myeongro.api.global.auth.oauth;

public interface OAuthConnectionRevoker {

	void revoke(String provider, String accessToken);
}
