package com.myeongro.api.global.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class OAuth2NextRequestFilterTests {

	private static final String ADULT_POLICY_VERSION = "2026-09-09";
	private final OAuth2NextRequestFilter filter =
		new OAuth2NextRequestFilter(ADULT_POLICY_VERSION);

	@Test
	void storesSafeFrontendPathForOAuthAuthorizationRequest() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest(
			"GET",
			"/oauth2/authorization/kakao"
		);
		request.setParameter("next", "/records?tab=latest");
		request.setParameter(
			OAuth2NextRequestFilter.ADULT_CONFIRMATION_PARAMETER,
			OAuth2NextRequestFilter.ADULT_CONFIRMATION_VALUE
		);

		filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

		assertThat(request.getSession().getAttribute(
			OAuth2NextRequestFilter.NEXT_SESSION_ATTRIBUTE
		)).isEqualTo("/records?tab=latest");
		assertThat(request.getSession().getAttribute(
			OAuth2NextRequestFilter.ADULT_VERSION_SESSION_ATTRIBUTE
		)).isEqualTo(ADULT_POLICY_VERSION);
	}

	@Test
	void storesRootWhenNextPathIsUnsafe() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest(
			"GET",
			"/oauth2/authorization/kakao"
		);
		request.setParameter("next", "//evil.example");
		request.setParameter(
			OAuth2NextRequestFilter.ADULT_CONFIRMATION_PARAMETER,
			OAuth2NextRequestFilter.ADULT_CONFIRMATION_VALUE
		);

		filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

		assertThat(request.getSession().getAttribute(
			OAuth2NextRequestFilter.NEXT_SESSION_ATTRIBUTE
		)).isEqualTo("/");
	}

	@Test
	void blocksOAuthBeforeRedirectWhenAdultConfirmationIsMissing() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest(
			"GET",
			"/oauth2/authorization/google"
		);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, new MockFilterChain());

		assertThat(response.getStatus()).isEqualTo(403);
		assertThat(request.getSession(false)).isNull();
	}
}
