package com.myeongro.api.global.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.ForwardedHeaderFilter;

class OAuth2NextRequestFilterTests {

	private final OAuth2NextRequestFilter filter =
		new OAuth2NextRequestFilter();

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

	@Test
	void doesNotCreateASessionWhenAForwardedPrefixFakesTheOAuthPath() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
		request.addHeader("X-Forwarded-Prefix", "/oauth2/authorization");
		var chain = new MockFilterChain(
			new jakarta.servlet.http.HttpServlet() {
			},
			new ForwardedHeaderFilter(),
			filter
		);

		chain.doFilter(request, new MockHttpServletResponse());

		assertThat(chain.getRequest()).isNotNull();
		assertThat(request.getSession(false)).isNull();
	}

	@Test
	void allowsOAuthToStartBeforeSignupAgeConfirmation() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest(
			"GET",
			"/oauth2/authorization/google"
		);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, new MockFilterChain());

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(request.getSession(false)).isNotNull();
	}
}
