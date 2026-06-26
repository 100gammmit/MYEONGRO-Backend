package com.myeongro.api.global.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.myeongro.api.domain.profile.repository.OAuthAccountRepository;

class DatabaseOAuthUserProvisionerTests {

	private final OAuthAccountRepository repository =
		org.mockito.Mockito.mock(OAuthAccountRepository.class);
	private final DatabaseOAuthUserProvisioner provisioner =
		new DatabaseOAuthUserProvisioner(repository);

	@Test
	void delegatesProvisioningToRepository() {
		OAuthProviderUserInfo userInfo = new OAuthProviderUserInfo(
			"kakao",
			"12345",
			"명로 사용자",
			"user@example.com"
		);
		ProvisionedOAuthUser expected = new ProvisionedOAuthUser(
			UUID.fromString("43bc72f9-eed1-4e4b-8717-6fe969b4ea43"),
			"명로 사용자",
			"kakao",
			"12345"
		);
		when(repository.provision(userInfo)).thenReturn(expected);

		assertThat(provisioner.provision(userInfo)).isEqualTo(expected);
		verify(repository).provision(userInfo);
	}
}
