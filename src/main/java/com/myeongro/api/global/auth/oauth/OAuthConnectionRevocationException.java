package com.myeongro.api.global.auth.oauth;

public class OAuthConnectionRevocationException extends RuntimeException {

	public OAuthConnectionRevocationException(String provider, Throwable cause) {
		super("Failed to revoke OAuth connection for provider: " + provider, cause);
	}
}
