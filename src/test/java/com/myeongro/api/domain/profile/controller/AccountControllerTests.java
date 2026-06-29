package com.myeongro.api.domain.profile.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.myeongro.api.domain.profile.service.AccountWithdrawalService;
import com.myeongro.api.global.auth.AuthExceptionHandler;
import com.myeongro.api.global.auth.AuthenticatedUser;
import com.myeongro.api.global.auth.AuthenticatedUserResolver;
import com.myeongro.api.global.auth.exception.UnauthenticatedUserException;
import com.myeongro.api.global.auth.session.SessionAuthenticatedPrincipal;

class AccountControllerTests {

	private static final UUID USER_ID =
		UUID.fromString("43bc72f9-eed1-4e4b-8717-6fe969b4ea43");

	private AuthenticatedUserResolver userResolver;
	private AccountWithdrawalService withdrawalService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		userResolver = org.mockito.Mockito.mock(AuthenticatedUserResolver.class);
		withdrawalService = org.mockito.Mockito.mock(AccountWithdrawalService.class);
		mockMvc = MockMvcBuilders
			.standaloneSetup(new AccountController(userResolver, withdrawalService))
			.setControllerAdvice(new AuthExceptionHandler())
			.build();
	}

	@Test
	void requiresAuthentication() throws Exception {
		when(userResolver.requireUser(null)).thenThrow(new UnauthenticatedUserException());

		mockMvc.perform(delete("/api/account"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void withdrawsAccountAndInvalidatesSession() throws Exception {
		TestingAuthenticationToken authentication = authentication();
		MockHttpSession session = new MockHttpSession();
		when(userResolver.requireUser(authentication)).thenReturn(new AuthenticatedUser(USER_ID));

		mockMvc.perform(delete("/api/account")
				.principal(authentication)
				.session(session))
			.andExpect(status().isNoContent());

		verify(withdrawalService).withdraw(USER_ID);
		assertThat(session.isInvalid()).isTrue();
	}

	private TestingAuthenticationToken authentication() {
		SessionAuthenticatedPrincipal principal = new SessionAuthenticatedPrincipal(
			USER_ID,
			"명로 사용자",
			"kakao",
			"12345"
		);
		TestingAuthenticationToken authentication = new TestingAuthenticationToken(
			principal,
			null
		);
		authentication.setAuthenticated(true);
		return authentication;
	}
}
