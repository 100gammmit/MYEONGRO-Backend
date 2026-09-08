package com.myeongro.api.domain.profile.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

class AccountSessionRevokerTests {

	private static final UUID USER_ID =
		UUID.fromString("43bc72f9-eed1-4e4b-8717-6fe969b4ea43");

	@Test
	void revokesEverySessionIndexedForTheDeletedAccount() {
		@SuppressWarnings("unchecked")
		FindByIndexNameSessionRepository<Session> repository =
			mock(FindByIndexNameSessionRepository.class);
		Session first = mock(Session.class);
		Session second = mock(Session.class);
		when(repository.findByPrincipalName(USER_ID.toString()))
			.thenReturn(Map.of("session-1", first, "session-2", second));

		new AccountSessionRevoker(repository).revokeAll(USER_ID);

		verify(repository).deleteById("session-1");
		verify(repository).deleteById("session-2");
	}
}
