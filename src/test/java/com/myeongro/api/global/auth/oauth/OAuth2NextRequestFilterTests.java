package com.myeongro.api.global.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class OAuth2NextRequestFilterTests {

	private final OAuth2NextRequestFilter filter = new OAuth2NextRequestFilter();

	@Test
	void storesSafeFrontendPathForOAuthAuthorizationRequest() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest(
			"GET",
			"/oauth2/authorization/kakao"
		);
		request.setParameter("next", "/records?tab=latest");

		filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

		assertThat(request.getSession().getAttribute(
			OAuth2NextRequestFilter.NEXT_SESSION_ATTRIBUTE
		)).isEqualTo("/records?tab=latest");
	}

	@Test
	void storesRootWhenNextPathIsUnsafe() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest(
			"GET",
			"/oauth2/authorization/kakao"
		);
		request.setParameter("next", "//evil.example");

		filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

		assertThat(request.getSession().getAttribute(
			OAuth2NextRequestFilter.NEXT_SESSION_ATTRIBUTE
		)).isEqualTo("/");
	}
}
