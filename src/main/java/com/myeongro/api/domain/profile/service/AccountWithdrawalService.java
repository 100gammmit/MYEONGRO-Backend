package com.myeongro.api.domain.profile.service;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.myeongro.api.domain.profile.repository.AccountWithdrawalRepository;

@Service
public class AccountWithdrawalService {

	private final AccountWithdrawalRepository repository;
	private final Clock clock;
	private final Duration purgeDelay;

	@Autowired
	public AccountWithdrawalService(
		AccountWithdrawalRepository repository,
		@Value("${app.account.retention.purge-delay:30d}") Duration purgeDelay
	) {
		this(repository, Clock.systemUTC(), purgeDelay);
	}

	AccountWithdrawalService(
		AccountWithdrawalRepository repository,
		Clock clock,
		Duration purgeDelay
	) {
		this.repository = repository;
		this.clock = clock;
		this.purgeDelay = purgeDelay;
	}

	@Transactional
	public void withdraw(UUID userId) {
		repository.withdraw(userId, clock.instant().plus(purgeDelay));
	}
}
