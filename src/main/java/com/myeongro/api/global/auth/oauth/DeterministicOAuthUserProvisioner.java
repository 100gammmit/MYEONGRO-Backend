package com.myeongro.api.global.auth.oauth;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class DeterministicOAuthUserProvisioner implements OAuthUserProvisioner {

	@Override
	public ProvisionedOAuthUser provision(OAuthProviderUserInfo userInfo) {
		String key = "oauth:%s:%s".formatted(userInfo.provider(), userInfo.providerUserId());
		UUID userId = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
		return new ProvisionedOAuthUser(
			userId,
			userInfo.displayName(),
			userInfo.provider(),
			userInfo.providerUserId()
		);
	}
}
