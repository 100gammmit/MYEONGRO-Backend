package com.myeongro.api.global.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;

class OAuth2LoginSuccessHandlerTests {

	private final OAuth2LoginSuccessHandler handler =
		new OAuth2LoginSuccessHandler("http://localhost:3000");

	@Test
	void redirectsToStoredFrontendPath() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.getSession().setAttribute(
			OAuth2NextRequestFilter.NEXT_SESSION_ATTRIBUTE,
			"/records?tab=latest"
		);
		MockHttpServletResponse response = new MockHttpServletResponse();

		handler.onAuthenticationSuccess(
			request,
			response,
			new TestingAuthenticationToken("user", null)
		);

		assertThat(response.getRedirectedUrl())
			.isEqualTo("http://localhost:3000/records?tab=latest");
		assertThat(request.getSession().getAttribute(
			OAuth2NextRequestFilter.NEXT_SESSION_ATTRIBUTE
		)).isNull();
	}

	@Test
	void fallsBackToRootWhenStoredPathIsUnsafe() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.getSession().setAttribute(
			OAuth2NextRequestFilter.NEXT_SESSION_ATTRIBUTE,
			"https://evil.example/phishing"
		);
		MockHttpServletResponse response = new MockHttpServletResponse();

		handler.onAuthenticationSuccess(
			request,
			response,
			new TestingAuthenticationToken("user", null)
		);

		assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/");
	}
}
