package com.myeongro.api.domain.eligibility.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.myeongro.api.global.auth.oauth.OAuthUserProvisioner;
import com.myeongro.api.global.auth.oauth.PendingSignupPrincipal;
import com.myeongro.api.global.auth.oauth.ProvisionedOAuthUser;

@Service
public class SignupCompletionService {

	private final OAuthUserProvisioner userProvisioner;
	private final AdultEligibilityService adultEligibilityService;

	public SignupCompletionService(
		OAuthUserProvisioner userProvisioner,
		AdultEligibilityService adultEligibilityService
	) {
		this.userProvisioner = userProvisioner;
		this.adultEligibilityService = adultEligibilityService;
	}

	@Transactional
	public ProvisionedOAuthUser complete(PendingSignupPrincipal pendingSignup) {
		ProvisionedOAuthUser user = userProvisioner.provision(pendingSignup.userInfo());
		adultEligibilityService.confirmSignupForUser(user.userId());
		return user;
	}

	@Transactional(readOnly = true)
	public Optional<ProvisionedOAuthUser> findCompleted(PendingSignupPrincipal pendingSignup) {
		return userProvisioner.findExisting(pendingSignup.userInfo())
			.filter(user -> adultEligibilityService.hasConfirmationForUser(user.userId()));
	}
}
