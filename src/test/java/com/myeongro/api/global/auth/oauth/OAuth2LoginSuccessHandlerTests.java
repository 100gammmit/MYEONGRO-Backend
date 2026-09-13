package com.myeongro.api.global.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import com.myeongro.api.domain.eligibility.service.AdultEligibilityService;
import com.myeongro.api.global.auth.AdultEligibilitySessionFilter;
import com.myeongro.api.global.auth.session.SessionAuthenticatedPrincipal;

class OAuth2LoginSuccessHandlerTests {

	private static final UUID USER_ID =
		UUID.fromString("43bc72f9-eed1-4e4b-8717-6fe969b4ea43");
	private final AdultEligibilityService eligibilityService =
		org.mockito.Mockito.mock(AdultEligibilityService.class);
	private final OAuth2LoginSuccessHandler handler =
		new OAuth2LoginSuccessHandler("http://localhost:3000", eligibilityService);

	@Test
	void redirectsAnExistingEligibleMemberToTheStoredFrontendPath() throws Exception {
		MockHttpServletRequest request = requestWithNext("/records?tab=latest");
		MockHttpServletResponse response = new MockHttpServletResponse();
		when(eligibilityService.hasConfirmationForUser(USER_ID)).thenReturn(true);

		handler.onAuthenticationSuccess(request, response, existingAuthentication());

		assertThat(response.getRedirectedUrl())
			.isEqualTo("http://localhost:3000/records?tab=latest");
		assertThat(request.getSession().getAttribute(
			OAuth2NextRequestFilter.NEXT_SESSION_ATTRIBUTE
		)).isNull();
		assertThat(request.getSession().getAttribute(
			AdultEligibilitySessionFilter.SESSION_ATTRIBUTE
		)).isEqualTo(AdultEligibilitySessionFilter.CONFIRMED_SESSION_VALUE);
	}

	@Test
	void redirectsANewOAuthUserToSignupAgeConfirmation() throws Exception {
		MockHttpServletRequest request = requestWithNext("/account");
		MockHttpServletResponse response = new MockHttpServletResponse();

		handler.onAuthenticationSuccess(request, response, pendingAuthentication());

		assertThat(response.getRedirectedUrl())
			.isEqualTo("http://localhost:3000/signup/age");
		assertThat(request.getSession().getAttribute(
			OAuth2NextRequestFilter.NEXT_SESSION_ATTRIBUTE
		)).isEqualTo("/account");
		assertThat(request.getSession().getAttribute(
			AdultEligibilitySessionFilter.SESSION_ATTRIBUTE
		)).isNull();
		SecurityContext context = (SecurityContext) request.getSession().getAttribute(
			HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY
		);
		assertThat(context.getAuthentication().getPrincipal())
			.isInstanceOf(PendingSignupSessionPrincipal.class);
		PendingSignupSessionPrincipal stored =
			(PendingSignupSessionPrincipal) context.getAuthentication().getPrincipal();
		assertThat(stored.provider()).isEqualTo("kakao");
		assertThat(stored.accessToken()).isEqualTo("access-token");
	}

	@Test
	void fallsBackToRootWhenStoredPathIsUnsafe() throws Exception {
		MockHttpServletRequest request = requestWithNext("https://evil.example/phishing");
		MockHttpServletResponse response = new MockHttpServletResponse();
		when(eligibilityService.hasConfirmationForUser(USER_ID)).thenReturn(true);

		handler.onAuthenticationSuccess(request, response, existingAuthentication());

		assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/");
	}

	@Test
	void rejectsAnExistingAccountWithoutARecordedSignupConfirmation() throws Exception {
		MockHttpServletRequest request = requestWithNext("/account");
		MockHttpServletResponse response = new MockHttpServletResponse();

		handler.onAuthenticationSuccess(request, response, existingAuthentication());

		assertThat(response.getRedirectedUrl()).isEqualTo(
			"http://localhost:3000/login?reason=signup-eligibility-missing&next=%2Faccount"
		);
	}

	private MockHttpServletRequest requestWithNext(String next) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.getSession().setAttribute(OAuth2NextRequestFilter.NEXT_SESSION_ATTRIBUTE, next);
		return request;
	}

	private TestingAuthenticationToken existingAuthentication() {
		var principal = new SessionAuthenticatedPrincipal(
			USER_ID,
			"명로 사용자",
			"google",
			"provider-user"
		);
		return new TestingAuthenticationToken(principal, null);
	}

	private TestingAuthenticationToken pendingAuthentication() {
		var principal = new PendingOAuth2User(
			new OAuthProviderUserInfo(
				"kakao",
				"12345",
				"명로 사용자",
				"user@example.com"
			),
			"access-token",
			Map.of("id", 12345L),
			List.of()
		);
		return new TestingAuthenticationToken(principal, null);
	}
}
