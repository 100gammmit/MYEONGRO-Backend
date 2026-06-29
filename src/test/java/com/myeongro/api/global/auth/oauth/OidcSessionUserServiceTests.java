package com.myeongro.api.global.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import com.myeongro.api.global.auth.session.SessionPrincipal;

class OidcSessionUserServiceTests {

	@Test
	void wrapsGoogleOidcUserAsSessionPrincipal() {
		OidcIdToken idToken = idToken(Map.of(
			"sub", "google-user-1",
			"name", "Myeongro User",
			"email", "user@example.com"
		));
		OidcUser googleUser = new DefaultOidcUser(
			List.of(new SimpleGrantedAuthority("ROLE_USER")),
			idToken,
			"sub"
		);
		UUID userId = UUID.fromString("43bc72f9-eed1-4e4b-8717-6fe969b4ea43");
		OidcSessionUserService service = new OidcSessionUserService(
			ignored -> googleUser,
			userInfo -> new ProvisionedOAuthUser(
				userId,
				userInfo.displayName(),
				userInfo.provider(),
				userInfo.providerUserId()
			),
			List.of(new GoogleOAuthProviderUserInfoExtractor())
		);

		OidcUser loaded = service.loadUser(userRequest("google"));

		assertThat(loaded).isInstanceOf(SessionPrincipal.class);
		SessionPrincipal principal = (SessionPrincipal) loaded;
		assertThat(principal.userId()).isEqualTo(userId);
		assertThat(principal.displayName()).isEqualTo("Myeongro User");
		assertThat(principal.provider()).isEqualTo("google");
		assertThat(principal.providerUserId()).isEqualTo("google-user-1");
		assertThat(loaded.getName()).isEqualTo(userId.toString());
		assertThat(loaded.getIdToken()).isSameAs(idToken);
	}

	@Test
	void rejectsUnsupportedOidcProviderBeforeUserInfoLookup() {
		OidcSessionUserService service = new OidcSessionUserService(
			ignored -> {
				throw new AssertionError("Unsupported OIDC providers must not call the user info endpoint");
			},
			userInfo -> {
				throw new AssertionError("Unsupported OIDC providers must not be provisioned");
			},
			List.of(new GoogleOAuthProviderUserInfoExtractor())
		);

		OAuth2AuthenticationException exception = catchThrowableOfType(
			() -> service.loadUser(userRequest("unknown")),
			OAuth2AuthenticationException.class
		);

		assertThat(exception.getError().getDescription())
			.isEqualTo("Unsupported OIDC provider: unknown");
	}

	private OidcUserRequest userRequest(String registrationId) {
		ClientRegistration registration = ClientRegistration
			.withRegistrationId(registrationId)
			.clientId("client-id")
			.clientSecret("client-secret")
			.redirectUri("http://localhost/login/oauth2/code/" + registrationId)
			.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
			.scope("openid", "profile", "email")
			.authorizationUri("https://example.com/oauth/authorize")
			.tokenUri("https://example.com/oauth/token")
			.jwkSetUri("https://example.com/oauth/jwks")
			.issuerUri("https://example.com")
			.userInfoUri("https://example.com/userinfo")
			.userNameAttributeName("sub")
			.build();
		return new OidcUserRequest(registration, accessToken(), idToken(Map.of("sub", "request-user")));
	}

	private OAuth2AccessToken accessToken() {
		return new OAuth2AccessToken(
			OAuth2AccessToken.TokenType.BEARER,
			"access-token",
			Instant.parse("2026-06-30T00:00:00Z"),
			Instant.parse("2026-06-30T01:00:00Z")
		);
	}

	private OidcIdToken idToken(Map<String, Object> claims) {
		return new OidcIdToken(
			"id-token",
			Instant.parse("2026-06-30T00:00:00Z"),
			Instant.parse("2026-06-30T01:00:00Z"),
			claims
		);
	}
}
