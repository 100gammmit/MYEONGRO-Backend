package com.myeongro.api.global.auth.session;

import java.io.Serializable;
import java.util.UUID;

public record SessionAuthenticatedPrincipal(
	UUID userId,
	String displayName,
	String provider,
	String providerUserId
) implements SessionPrincipal, Serializable {
}
