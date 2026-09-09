package com.myeongro.api.global.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.myeongro.api.global.auth.session.SessionAuthenticatedPrincipal;

class AdultEligibilitySessionFilterTests {

	private static final UUID USER_ID =
		UUID.fromString("43bc72f9-eed1-4e4b-8717-6fe969b4ea43");
	private static final String VERSION = "2026-09-09";

	private final AdultEligibilitySessionFilter filter =
		new AdultEligibilitySessionFilter(VERSION);

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void invalidatesAnAuthenticatedSessionWithoutTheCurrentConfirmation() throws Exception {
		MockHttpServletRequest request = authenticatedRequest();
		MockHttpSession session = (MockHttpSession) request.getSession(false);

		filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

		assertThat(session.isInvalid()).isTrue();
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
	}

	@Test
	void keepsAnAuthenticatedSessionWithTheCurrentConfirmation() throws Exception {
		MockHttpServletRequest request = authenticatedRequest();
		MockHttpSession session = (MockHttpSession) request.getSession(false);
		session.setAttribute(AdultEligibilitySessionFilter.SESSION_ATTRIBUTE, VERSION);

		filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

		assertThat(session.isInvalid()).isFalse();
		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
	}

	private MockHttpServletRequest authenticatedRequest() {
		SessionAuthenticatedPrincipal principal = new SessionAuthenticatedPrincipal(
			USER_ID,
			"명로 사용자",
			"google",
			"provider-user"
		);
		TestingAuthenticationToken authentication =
			new TestingAuthenticationToken(principal, null);
		authentication.setAuthenticated(true);
		SecurityContextHolder.getContext().setAuthentication(authentication);

		MockHttpServletRequest request = new MockHttpServletRequest();
		request.getSession(true);
		return request;
	}
}
