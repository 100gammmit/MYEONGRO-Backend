package com.myeongro.api.global.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeterministicOAuthUserProvisionerTests {

	private final DeterministicOAuthUserProvisioner provisioner =
		new DeterministicOAuthUserProvisioner();

	@Test
	void createsStableInternalUserIdFromProviderAccount() {
		OAuthProviderUserInfo userInfo = new OAuthProviderUserInfo(
			"kakao",
			"12345",
			"명로 사용자",
			null
		);

		assertThat(provisioner.provision(userInfo).userId())
			.isEqualTo(provisioner.provision(userInfo).userId());
	}

	@Test
	void separatesDifferentProvidersEvenWhenProviderUserIdIsSame() {
		OAuthProviderUserInfo kakao = new OAuthProviderUserInfo("kakao", "12345", null, null);
		OAuthProviderUserInfo google = new OAuthProviderUserInfo("google", "12345", null, null);

		assertThat(provisioner.provision(kakao).userId())
			.isNotEqualTo(provisioner.provision(google).userId());
	}
}
