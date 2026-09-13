package com.myeongro.api.global.auth.oauth;

import java.io.Serializable;

public record PendingSignupSessionPrincipal(
	String provider,
	String providerUserId,
	String displayName,
	String email,
	String accessToken
) implements PendingSignupPrincipal, Serializable {

	private static final long serialVersionUID = 1L;

	public static PendingSignupSessionPrincipal from(PendingSignupPrincipal principal) {
		return new PendingSignupSessionPrincipal(
			principal.provider(),
			principal.providerUserId(),
			principal.displayName(),
			principal.email(),
			principal.accessToken()
		);
	}
}
