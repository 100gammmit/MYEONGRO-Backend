package com.myeongro.api.global.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;

import com.myeongro.api.global.auth.exception.InvalidAuthenticatedUserException;
import com.myeongro.api.global.auth.exception.UnauthenticatedUserException;
import com.myeongro.api.global.auth.session.SessionAuthenticatedPrincipal;

class AuthenticatedUserResolverTests {

	private final AuthenticatedUserResolver resolver = new AuthenticatedUserResolver();

	@Test
	void resolvesUserIdFromSessionPrincipal() {
		UUID userId = UUID.fromString("43bc72f9-eed1-4e4b-8717-6fe969b4ea43");

		AuthenticatedUser user = resolver.requireUser(sessionAuthentication(userId));

		assertThat(user.id()).isEqualTo(userId);
	}

	@Test
	void rejectsMissingSessionPrincipal() {
		TestingAuthenticationToken authentication =
			new TestingAuthenticationToken("anonymous", null);

		assertThatThrownBy(() -> resolver.requireUser(authentication))
			.isInstanceOf(UnauthenticatedUserException.class);
	}

	@Test
	void rejectsAuthenticatedPrincipalWithoutUserId() {
		TestingAuthenticationToken authentication =
			new TestingAuthenticationToken(new Object(), null);
		authentication.setAuthenticated(true);

		assertThatThrownBy(() -> resolver.requireUser(authentication))
			.isInstanceOf(InvalidAuthenticatedUserException.class);
	}

	private TestingAuthenticationToken sessionAuthentication(UUID userId) {
		SessionAuthenticatedPrincipal principal = new SessionAuthenticatedPrincipal(
			userId,
			"명로 사용자",
			"kakao",
			"12345"
		);
		TestingAuthenticationToken authentication = new TestingAuthenticationToken(
			principal,
			null
		);
		authentication.setAuthenticated(true);
		return authentication;
	}
}
