package com.myeongro.api.domain.profile.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.myeongro.api.domain.profile.repository.AccountPurgeRepository;

class AccountPurgeServiceTests {

	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

	private final AccountPurgeRepository repository =
		org.mockito.Mockito.mock(AccountPurgeRepository.class);

	@Test
	void skipsPurgeWhenDisabled() {
		var service = new AccountPurgeService(
			repository,
			Clock.fixed(NOW, ZoneOffset.UTC),
			false,
			100
		);

		service.purgeDueAccounts();

		verify(repository, never()).purgeDueProfiles(org.mockito.Mockito.any(), org.mockito.Mockito.anyInt());
	}

	@Test
	void purgesDueAccountsWhenEnabled() {
		var service = new AccountPurgeService(
			repository,
			Clock.fixed(NOW, ZoneOffset.UTC),
			true,
			100
		);

		service.purgeDueAccounts();

		verify(repository).purgeDueProfiles(NOW, 100);
	}
}
