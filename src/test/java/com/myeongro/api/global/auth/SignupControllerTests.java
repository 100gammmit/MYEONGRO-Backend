package com.myeongro.api.global.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import com.myeongro.api.domain.eligibility.service.SignupCompletionService;
import com.myeongro.api.global.auth.oauth.OAuth2NextRequestFilter;
import com.myeongro.api.global.auth.oauth.OAuthConnectionRevocationException;
import com.myeongro.api.global.auth.oauth.OAuthConnectionRevoker;
import com.myeongro.api.global.auth.oauth.OAuthProviderUserInfo;
import com.myeongro.api.global.auth.oauth.PendingOAuth2User;
import com.myeongro.api.global.auth.oauth.ProvisionedOAuthUser;
import com.myeongro.api.global.auth.session.SessionPrincipal;

class SignupControllerTests {

	private static final UUID USER_ID =
		UUID.fromString("43bc72f9-eed1-4e4b-8717-6fe969b4ea43");
	private final SignupCompletionService signupCompletionService =
		org.mockito.Mockito.mock(SignupCompletionService.class);
	private final OAuthConnectionRevoker connectionRevoker =
		org.mockito.Mockito.mock(OAuthConnectionRevoker.class);
	private final SignupController controller = new SignupController(
		signupCompletionService,
		connectionRevoker
	);

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void exposesOnlyThePendingProviderInSignupStatus() {
		var response = controller.status(pendingAuthentication());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).containsEntry("pending", true);
		assertThat(response.getBody()).containsEntry("provider", "kakao");
	}

	@Test
	void completesAccountCreationAndReplacesThePendingSecurityContext() {
		TestingAuthenticationToken authentication = pendingAuthentication();
		PendingOAuth2User pending = (PendingOAuth2User) authentication.getPrincipal();
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpSession session = (MockHttpSession) request.getSession(true);
		session.setAttribute(OAuth2NextRequestFilter.NEXT_SESSION_ATTRIBUTE, "/records?tab=latest");
		when(signupCompletionService.complete(pending)).thenReturn(new ProvisionedOAuthUser(
			USER_ID,
			"명로 사용자",
			"kakao",
			"12345"
		));

		var response = controller.confirmAdultEligibility(authentication, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).containsEntry("next", "/records?tab=latest");
		assertThat(session.getAttribute(OAuth2NextRequestFilter.NEXT_SESSION_ATTRIBUTE)).isNull();
		assertThat(session.getAttribute(AdultEligibilitySessionFilter.SESSION_ATTRIBUTE))
			.isEqualTo(AdultEligibilitySessionFilter.CONFIRMED_SESSION_VALUE);
		SecurityContext context = (SecurityContext) session.getAttribute(
			HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY
		);
		assertThat(context.getAuthentication().getPrincipal()).isInstanceOf(SessionPrincipal.class);
		assertThat(((SessionPrincipal) context.getAuthentication().getPrincipal()).userId())
			.isEqualTo(USER_ID);
	}

	@Test
	void revokesTheProviderConnectionAndInvalidatesThePendingSessionOnCancel() {
		TestingAuthenticationToken authentication = pendingAuthentication();
		MockHttpServletRequest request = requestWithSession();
		MockHttpSession session = (MockHttpSession) request.getSession(false);

		var response = controller.cancel(authentication, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		verify(connectionRevoker).revoke("kakao", "access-token");
		assertThat(session.isInvalid()).isTrue();
	}

	@Test
	void removesThePendingSessionEvenWhenProviderUnlinkFails() {
		TestingAuthenticationToken authentication = pendingAuthentication();
		MockHttpServletRequest request = requestWithSession();
		MockHttpSession session = (MockHttpSession) request.getSession(false);
		doThrow(new OAuthConnectionRevocationException("kakao", new RuntimeException()))
			.when(connectionRevoker).revoke("kakao", "access-token");

		var response = controller.cancel(authentication, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
		assertThat(response.getBody()).containsEntry("code", "OAUTH_UNLINK_FAILED");
		assertThat(session.isInvalid()).isTrue();
	}

	@Test
	void rejectsSignupActionsWithoutAPendingOauthPrincipal() {
		var authentication = new TestingAuthenticationToken("guest", null);

		assertThat(controller.status(authentication).getStatusCode())
			.isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(controller.confirmAdultEligibility(
			authentication,
			new MockHttpServletRequest()
		).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
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
		var authentication = new TestingAuthenticationToken(principal, null);
		authentication.setAuthenticated(true);
		return authentication;
	}

	private MockHttpServletRequest requestWithSession() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.getSession(true);
		return request;
	}
}
