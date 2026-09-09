package com.myeongro.api.global.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;

import com.myeongro.api.domain.eligibility.service.AdultEligibilityService;
import com.myeongro.api.global.auth.AdultEligibilitySessionFilter;
import com.myeongro.api.global.auth.session.SessionAuthenticatedPrincipal;

class OAuth2LoginSuccessHandlerTests {

	private static final UUID USER_ID =
		UUID.fromString("43bc72f9-eed1-4e4b-8717-6fe969b4ea43");
	private static final String ADULT_POLICY_VERSION = "2026-09-09";
	private final AdultEligibilityService eligibilityService =
		org.mockito.Mockito.mock(AdultEligibilityService.class);
	private final OAuth2LoginSuccessHandler handler =
		new OAuth2LoginSuccessHandler("http://localhost:3000", eligibilityService);

	@Test
	void redirectsToStoredFrontendPath() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.getSession().setAttribute(
			OAuth2NextRequestFilter.NEXT_SESSION_ATTRIBUTE,
			"/records?tab=latest"
		);
		confirmAdultEligibility(request);
		MockHttpServletResponse response = new MockHttpServletResponse();

		handler.onAuthenticationSuccess(
			request,
			response,
			authentication()
		);

		assertThat(response.getRedirectedUrl())
			.isEqualTo("http://localhost:3000/records?tab=latest");
		assertThat(request.getSession().getAttribute(
			OAuth2NextRequestFilter.NEXT_SESSION_ATTRIBUTE
		)).isNull();
		assertThat(request.getSession().getAttribute(
			AdultEligibilitySessionFilter.SESSION_ATTRIBUTE
		)).isEqualTo(ADULT_POLICY_VERSION);
		verify(eligibilityService).confirmCurrentForUser(USER_ID, ADULT_POLICY_VERSION);
	}

	@Test
	void fallsBackToRootWhenStoredPathIsUnsafe() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.getSession().setAttribute(
			OAuth2NextRequestFilter.NEXT_SESSION_ATTRIBUTE,
			"https://evil.example/phishing"
		);
		confirmAdultEligibility(request);
		MockHttpServletResponse response = new MockHttpServletResponse();

		handler.onAuthenticationSuccess(
			request,
			response,
			authentication()
		);

		assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/");
	}

	@Test
	void rejectsACompletedOAuthCallbackWithoutTheAdultConfirmation() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.getSession().setAttribute(
			OAuth2NextRequestFilter.NEXT_SESSION_ATTRIBUTE,
			"/account"
		);
		MockHttpServletResponse response = new MockHttpServletResponse();

		handler.onAuthenticationSuccess(request, response, authentication());

		assertThat(response.getRedirectedUrl()).isEqualTo(
			"http://localhost:3000/login?reason=adult-eligibility-required&next=%2Faccount"
		);
	}

	private void confirmAdultEligibility(MockHttpServletRequest request) {
		when(eligibilityService.isCurrentVersion(ADULT_POLICY_VERSION)).thenReturn(true);
		request.getSession().setAttribute(
			OAuth2NextRequestFilter.ADULT_VERSION_SESSION_ATTRIBUTE,
			ADULT_POLICY_VERSION
		);
	}

	private TestingAuthenticationToken authentication() {
		var principal = new SessionAuthenticatedPrincipal(
			USER_ID,
			"명로 사용자",
			"google",
			"provider-user"
		);
		return new TestingAuthenticationToken(principal, null);
	}
}
