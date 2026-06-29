package com.myeongro.api.domain.profile.service;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.myeongro.api.domain.profile.repository.AccountPurgeRepository;

@Service
public class AccountPurgeService {

	private final AccountPurgeRepository repository;
	private final Clock clock;
	private final boolean enabled;
	private final int batchSize;

	@Autowired
	public AccountPurgeService(
		AccountPurgeRepository repository,
		@Value("${app.account.retention.purge-enabled:false}") boolean enabled,
		@Value("${app.account.retention.purge-batch-size:100}") int batchSize
	) {
		this(repository, Clock.systemUTC(), enabled, batchSize);
	}

	AccountPurgeService(
		AccountPurgeRepository repository,
		Clock clock,
		boolean enabled,
		int batchSize
	) {
		this.repository = repository;
		this.clock = clock;
		this.enabled = enabled;
		this.batchSize = batchSize;
	}

	@Scheduled(cron = "${app.account.retention.purge-cron:0 0 4 * * *}")
	public void purgeDueAccounts() {
		if (!enabled) {
			return;
		}

		repository.purgeDueProfiles(clock.instant(), batchSize);
	}
}
