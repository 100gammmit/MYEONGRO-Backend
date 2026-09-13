package com.myeongro.api.global.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import com.myeongro.api.domain.eligibility.service.AdultEligibilityService;
import com.myeongro.api.global.auth.AdultEligibilitySessionFilter;
import com.myeongro.api.global.auth.session.SessionAuthenticatedPrincipal;

class OAuth2LoginSuccessHandlerTests {

	private static final UUID USER_ID =
		UUID.fromString("43bc72f9-eed1-4e4b-8717-6fe969b4ea43");
	private final AdultEligibilityService eligibilityService =
		org.mockito.Mockito.mock(AdultEligibilityService.class);
	private final SignupAttemptCoordinator signupAttemptCoordinator =
		org.mockito.Mockito.mock(SignupAttemptCoordinator.class);
	private final OAuth2LoginSuccessHandler handler =
		new OAuth2LoginSuccessHandler(
			"http://localhost:3000",
			eligibilityService,
			signupAttemptCoordinator
		);

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

		when(signupAttemptCoordinator.beginAttempt()).thenReturn("attempt-1");
		handler.onAuthenticationSuccess(request, response, pendingAuthenticationWithSourceAuthority());

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
		assertThat(stored.attemptId()).isEqualTo("attempt-1");
		assertThat(stored.provider()).isEqualTo("kakao");
		assertThat(stored.accessToken()).isEqualTo("access-token");
		assertThat(context.getAuthentication().getAuthorities())
			.allMatch(authority -> authority.getClass().equals(
				org.springframework.security.core.authority.SimpleGrantedAuthority.class
			));
		String serialized = new String(serialize(context), StandardCharsets.ISO_8859_1);
		assertThat(serialized).doesNotContain("source-sensitive-value");
	}

	@Test
	void removesOidcTokenAndClaimsFromTheRedisPendingSecurityContext() throws Exception {
		MockHttpServletRequest request = requestWithNext("/records");
		MockHttpServletResponse response = new MockHttpServletResponse();
		OidcIdToken idToken = new OidcIdToken(
			"source-oidc-token",
			Instant.parse("2026-09-13T00:00:00Z"),
			Instant.parse("2026-09-13T01:00:00Z"),
			Map.of("sub", "12345", "source-oidc-claim", "source-oidc-value")
		);
		var authentication = UsernamePasswordAuthenticationToken.authenticated(
			pendingAuthentication().getPrincipal(),
			null,
			List.of(new OidcUserAuthority(idToken))
		);
		when(signupAttemptCoordinator.beginAttempt()).thenReturn("attempt-oidc");

		handler.onAuthenticationSuccess(request, response, authentication);

		SecurityContext context = (SecurityContext) request.getSession().getAttribute(
			HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY
		);
		String serialized = new String(serialize(context), StandardCharsets.ISO_8859_1);
		assertThat(serialized)
			.doesNotContain("source-oidc-token")
			.doesNotContain("source-oidc-value");
		assertThat(context.getAuthentication().getAuthorities())
			.allMatch(authority -> authority.getClass().equals(
				org.springframework.security.core.authority.SimpleGrantedAuthority.class
			));
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

	private UsernamePasswordAuthenticationToken pendingAuthenticationWithSourceAuthority() {
		PendingSignupPrincipal principal = (PendingSignupPrincipal) pendingAuthentication().getPrincipal();
		return UsernamePasswordAuthenticationToken.authenticated(
			principal,
			null,
			List.of(new OAuth2UserAuthority(Map.of(
				"id", "12345",
				"source-sensitive-claim", "source-sensitive-value"
			)))
		);
	}

	private byte[] serialize(Object value) throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
			output.writeObject(value);
		}
		return bytes.toByteArray();
	}
}
