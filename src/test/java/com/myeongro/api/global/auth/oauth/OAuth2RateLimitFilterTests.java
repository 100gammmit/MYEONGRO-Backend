package com.myeongro.api.global.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.ForwardedHeaderFilter;

import com.myeongro.api.global.ratelimit.IpRateLimiter;

class OAuth2RateLimitFilterTests {

	private final IpRateLimiter ipRateLimiter = mock(IpRateLimiter.class);
	private final OAuth2RateLimitFilter filter =
		new OAuth2RateLimitFilter("http://localhost:3000/", ipRateLimiter);

	private MockHttpServletRequest request(String path) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
		request.setRemoteAddr("203.0.113.7");
		return request;
	}

	@Test
	void passesAnAllowedOauthStartThrough() throws Exception {
		when(ipRateLimiter.tryAcquire(IpRateLimiter.Scope.OAUTH_START, "203.0.113.7")).thenReturn(true);
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request("/oauth2/authorization/kakao"), new MockHttpServletResponse(), chain);

		assertThat(chain.getRequest()).isNotNull();
	}

	@Test
	void redirectsARejectedStartBackToLoginWithItsNextPath() throws Exception {
		MockHttpServletRequest request = request("/oauth2/authorization/kakao");
		request.setParameter("next", "/records?tab=latest");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		assertThat(chain.getRequest()).isNull();
		assertThat(request.getSession(false)).isNull();
		assertThat(response.getRedirectedUrl()).isEqualTo(
			"http://localhost:3000/login?reason=rate-limited&next=%2Frecords%3Ftab%3Dlatest"
		);
	}

	@Test
	void countsCallbacksInTheirOwnScope() throws Exception {
		when(ipRateLimiter.tryAcquire(IpRateLimiter.Scope.OAUTH_CALLBACK, "203.0.113.7")).thenReturn(false);
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request("/login/oauth2/code/kakao"), response, chain);

		assertThat(chain.getRequest()).isNull();
		assertThat(response.getRedirectedUrl())
			.isEqualTo("http://localhost:3000/login?reason=rate-limited&next=%2F");
	}

	@Test
	void ignoresOtherPaths() throws Exception {
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request("/api/auth/me"), new MockHttpServletResponse(), chain);

		assertThat(chain.getRequest()).isNotNull();
		verifyNoInteractions(ipRateLimiter);
	}

	@Test
	void stillLimitsAnOauthStartThatCarriesAForwardedPrefix() throws Exception {
		MockHttpServletRequest request = request("/oauth2/authorization/kakao");
		request.addHeader("X-Forwarded-Prefix", "/other");
		MockHttpServletResponse response = new MockHttpServletResponse();
		var chain = new MockFilterChain(
			new jakarta.servlet.http.HttpServlet() {
			},
			new ForwardedHeaderFilter(),
			filter
		);

		chain.doFilter(request, response);

		assertThat(response.getRedirectedUrl())
			.isEqualTo("http://localhost:3000/login?reason=rate-limited&next=%2F");
	}
}
