package com.myeongro.api.domain.profile.service;

import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.myeongro.api.domain.profile.repository.AccountWithdrawalRepository;

class AccountWithdrawalServiceTests {

	private static final UUID USER_ID =
		UUID.fromString("43bc72f9-eed1-4e4b-8717-6fe969b4ea43");
	private static final Instant NOW = Instant.parse("2026-06-29T00:00:00Z");

	private final AccountWithdrawalRepository repository =
		org.mockito.Mockito.mock(AccountWithdrawalRepository.class);
	private final AccountWithdrawalService service =
		new AccountWithdrawalService(
			repository,
			Clock.fixed(NOW, ZoneOffset.UTC),
			Duration.ofDays(30)
		);

	@Test
	void withdrawsTheCurrentUserAccountWithRetentionDeadline() {
		service.withdraw(USER_ID);

		verify(repository).withdraw(USER_ID, Instant.parse("2026-07-29T00:00:00Z"));
	}
}
