package com.myeongro.api.global.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import com.myeongro.api.global.auth.exception.InvalidAuthenticatedUserException;
import com.myeongro.api.global.auth.exception.UnauthenticatedUserException;

class AuthenticatedUserResolverTests {

	private final AuthenticatedUserResolver resolver = new AuthenticatedUserResolver();

	@Test
	void resolvesUserIdFromJwtSubject() {
		UUID userId = UUID.fromString("43bc72f9-eed1-4e4b-8717-6fe969b4ea43");

		AuthenticatedUser user = resolver.requireUser(jwtAuthentication(userId.toString()));

		assertThat(user.id()).isEqualTo(userId);
	}

	@Test
	void rejectsMissingJwtPrincipal() {
		TestingAuthenticationToken authentication =
			new TestingAuthenticationToken("anonymous", null);

		assertThatThrownBy(() -> resolver.requireUser(authentication))
			.isInstanceOf(UnauthenticatedUserException.class);
	}

	@Test
	void rejectsNonUuidSubject() {
		assertThatThrownBy(() -> resolver.requireUser(jwtAuthentication("not-a-uuid")))
			.isInstanceOf(InvalidAuthenticatedUserException.class);
	}

	private TestingAuthenticationToken jwtAuthentication(String subject) {
		Jwt jwt = new Jwt(
			"token",
			Instant.parse("2026-06-20T00:00:00Z"),
			Instant.parse("2026-06-20T01:00:00Z"),
			Map.of("alg", "RS256"),
			Map.of("sub", subject)
		);
		return new TestingAuthenticationToken(jwt, null);
	}
}
