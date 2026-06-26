package com.myeongro.api.global.auth.oauth;

import java.util.UUID;

public record ProvisionedOAuthUser(
	UUID userId,
	String displayName,
	String provider,
	String providerUserId
) {
}
