package com.myeongro.api.global.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.myeongro.api.global.auth.oauth.OAuthProviderUserInfo;
import com.myeongro.api.global.auth.oauth.PendingOAuth2User;

class PendingSignupAccessFilterTests {

	private final PendingSignupAccessFilter filter = new PendingSignupAccessFilter();

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void blocksPendingSignupFromUsingMemberApis() throws Exception {
		setPendingAuthentication();
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/tarot/readings");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(chain.getRequest()).isNull();
	}

	@Test
	void allowsPendingSignupToUseOnlySignupStatusAndCompletionApis() throws Exception {
		for (String path : List.of(
			"/api/signup",
			"/api/signup/adult-eligibility",
			"/api/auth/me"
		)) {
			setPendingAuthentication();
			MockFilterChain chain = new MockFilterChain();

			filter.doFilter(
				new MockHttpServletRequest("GET", path),
				new MockHttpServletResponse(),
				chain
			);

			assertThat(chain.getRequest()).isNotNull();
		}
	}

	@Test
	void blocksGenericLogoutSoExplicitCancellationCannotSkipProviderUnlink() throws Exception {
		setPendingAuthentication();
		MockFilterChain chain = new MockFilterChain();
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(
			new MockHttpServletRequest("POST", "/api/auth/logout"),
			response,
			chain
		);

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(chain.getRequest()).isNull();
	}

	private void setPendingAuthentication() {
		var principal = new PendingOAuth2User(
			new OAuthProviderUserInfo("google", "google-user", "명로 사용자", "user@example.com"),
			"access-token",
			Map.of("sub", "google-user"),
			List.of()
		);
		var authentication = new TestingAuthenticationToken(principal, null);
		authentication.setAuthenticated(true);
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}
}
