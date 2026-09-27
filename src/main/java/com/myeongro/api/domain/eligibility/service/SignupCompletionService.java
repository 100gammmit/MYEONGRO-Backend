package com.myeongro.api.domain.eligibility.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.myeongro.api.global.auth.oauth.OAuthUserProvisioner;
import com.myeongro.api.global.auth.oauth.PendingSignupSessionPrincipal;
import com.myeongro.api.global.auth.oauth.ProvisionedOAuthUser;
import com.myeongro.api.domain.consent.service.ConsentService;

@Service
public class SignupCompletionService {

	private final OAuthUserProvisioner userProvisioner;
	private final AdultEligibilityService adultEligibilityService;
	private final ConsentService consentService;

	public SignupCompletionService(
		OAuthUserProvisioner userProvisioner,
		AdultEligibilityService adultEligibilityService,
		ConsentService consentService
	) {
		this.userProvisioner = userProvisioner;
		this.adultEligibilityService = adultEligibilityService;
		this.consentService = consentService;
	}

	@Transactional
	public ProvisionedOAuthUser complete(
		PendingSignupSessionPrincipal pendingSignup,
		String termsVersion
	) {
		ProvisionedOAuthUser user = userProvisioner.provision(pendingSignup.userInfo());
		adultEligibilityService.confirmSignupForUser(
			user.userId(),
			pendingSignup.attemptGenerationId()
		);
		consentService.acceptTermsForSignup(user.userId(), termsVersion);
		return user;
	}

	@Transactional(readOnly = true)
	public Optional<ProvisionedOAuthUser> findCompleted(PendingSignupSessionPrincipal pendingSignup) {
		return userProvisioner.findExisting(pendingSignup.userInfo())
			.filter(user -> adultEligibilityService.hasSignupConfirmation(
				user.userId(),
				pendingSignup.attemptGenerationId()
			));
	}
}
