package com.myeongro.api.domain.eligibility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.myeongro.api.global.auth.oauth.OAuthProviderUserInfo;
import com.myeongro.api.global.auth.oauth.OAuthUserProvisioner;
import com.myeongro.api.global.auth.oauth.PendingSignupPrincipal;
import com.myeongro.api.global.auth.oauth.ProvisionedOAuthUser;

class SignupCompletionServiceTests {

	private static final UUID USER_ID =
		UUID.fromString("43bc72f9-eed1-4e4b-8717-6fe969b4ea43");

	private final OAuthUserProvisioner userProvisioner =
		org.mockito.Mockito.mock(OAuthUserProvisioner.class);
	private final AdultEligibilityService adultEligibilityService =
		org.mockito.Mockito.mock(AdultEligibilityService.class);
	private final SignupCompletionService service = new SignupCompletionService(
		userProvisioner,
		adultEligibilityService
	);

	@Test
	void createsTheAccountBeforeRecordingSignupEligibilityInOneServiceBoundary() {
		PendingSignupPrincipal pending = org.mockito.Mockito.mock(PendingSignupPrincipal.class);
		OAuthProviderUserInfo userInfo = new OAuthProviderUserInfo(
			"google",
			"google-user",
			"명로 사용자",
			"user@example.com"
		);
		ProvisionedOAuthUser user = new ProvisionedOAuthUser(
			USER_ID,
			"명로 사용자",
			"google",
			"google-user"
		);
		when(pending.userInfo()).thenReturn(userInfo);
		when(userProvisioner.provision(userInfo)).thenReturn(user);

		assertThat(service.complete(pending)).isEqualTo(user);

		InOrder order = inOrder(userProvisioner, adultEligibilityService);
		order.verify(userProvisioner).provision(userInfo);
		order.verify(adultEligibilityService).confirmSignupForUser(USER_ID);
	}
}
