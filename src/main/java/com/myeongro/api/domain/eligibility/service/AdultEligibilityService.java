package com.myeongro.api.domain.eligibility.service;

import java.time.Clock;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.myeongro.api.domain.eligibility.repository.AdultEligibilityRepository;

@Service
public class AdultEligibilityService {

	static final String CONFIRMATION_METHOD = "oauth-signup-self-declaration";

	private final AdultEligibilityRepository repository;
	private final String currentVersion;
	private final Clock clock;

	@Autowired
	public AdultEligibilityService(
		AdultEligibilityRepository repository,
		@Value("${app.eligibility.adult-policy-version}") String currentVersion
	) {
		this(repository, currentVersion, Clock.systemUTC());
	}

	AdultEligibilityService(
		AdultEligibilityRepository repository,
		String currentVersion,
		Clock clock
	) {
		this.repository = repository;
		this.currentVersion = currentVersion;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public boolean hasConfirmationForUser(UUID userId) {
		return repository.hasConfirmation(userId);
	}

	@Transactional(readOnly = true)
	public boolean hasSignupConfirmation(UUID userId, String signupGenerationId) {
		return repository.hasSignupConfirmation(userId, signupGenerationId);
	}

	@Transactional
	public void confirmSignupForUser(UUID userId, String signupGenerationId) {
		repository.saveConfirmation(
			userId,
			currentVersion,
			clock.instant(),
			CONFIRMATION_METHOD,
			signupGenerationId
		);
	}
}
