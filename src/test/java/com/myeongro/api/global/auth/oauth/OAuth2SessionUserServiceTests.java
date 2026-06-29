package com.myeongro.api.global.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import com.myeongro.api.global.auth.session.SessionPrincipal;

class OAuth2SessionUserServiceTests {

	@Test
	void wrapsKakaoUserInfoAsSessionPrincipal() {
		OAuth2User kakaoUser = new DefaultOAuth2User(
			List.of(new SimpleGrantedAuthority("ROLE_USER")),
			Map.of(
				"id",
				12345L,
				"kakao_account",
				Map.of("email", "user@example.com"),
				"properties",
				Map.of("nickname", "명로 사용자")
			),
			"id"
		);
		UUID userId = UUID.fromString("43bc72f9-eed1-4e4b-8717-6fe969b4ea43");
		OAuth2SessionUserService service = new OAuth2SessionUserService(
			ignored -> kakaoUser,
			userInfo -> new ProvisionedOAuthUser(
				userId,
				userInfo.displayName(),
				userInfo.provider(),
				userInfo.providerUserId()
			),
			List.of(new KakaoOAuthProviderUserInfoExtractor())
		);

		OAuth2User loaded = service.loadUser(userRequest("kakao"));

		assertThat(loaded).isInstanceOf(SessionPrincipal.class);
		SessionPrincipal principal = (SessionPrincipal) loaded;
		assertThat(principal.userId()).isEqualTo(userId);
		assertThat(principal.displayName()).isEqualTo("명로 사용자");
		assertThat(principal.provider()).isEqualTo("kakao");
		assertThat(principal.providerUserId()).isEqualTo("12345");
		assertThat(loaded.getName()).isEqualTo(userId.toString());
		assertThat((Object) loaded.getAttribute("id")).isEqualTo(12345L);
	}

	@Test
	void wrapsGoogleUserInfoAsSeparateProviderSessionPrincipal() {
		OAuth2User googleUser = new DefaultOAuth2User(
			List.of(new SimpleGrantedAuthority("ROLE_USER")),
			Map.of(
				"sub",
				"google-user-1",
				"name",
				"Myeongro User",
				"email",
				"user@example.com"
			),
			"sub"
		);
		UUID userId = UUID.fromString("43bc72f9-eed1-4e4b-8717-6fe969b4ea43");
		OAuth2SessionUserService service = new OAuth2SessionUserService(
			ignored -> googleUser,
			userInfo -> new ProvisionedOAuthUser(
				userId,
				userInfo.displayName(),
				userInfo.provider(),
				userInfo.providerUserId()
			),
			List.of(
				new KakaoOAuthProviderUserInfoExtractor(),
				new GoogleOAuthProviderUserInfoExtractor()
			)
		);

		OAuth2User loaded = service.loadUser(userRequest("google", "sub"));

		SessionPrincipal principal = (SessionPrincipal) loaded;
		assertThat(principal.userId()).isEqualTo(userId);
		assertThat(principal.displayName()).isEqualTo("Myeongro User");
		assertThat(principal.provider()).isEqualTo("google");
		assertThat(principal.providerUserId()).isEqualTo("google-user-1");
		assertThat(loaded.getName()).isEqualTo(userId.toString());
	}

	@Test
	void rejectsUnsupportedOAuthProvider() {
		OAuth2SessionUserService service = new OAuth2SessionUserService(
			ignored -> {
				throw new AssertionError("Unsupported providers must not call the user info endpoint");
			},
			userInfo -> {
				throw new AssertionError("Unsupported providers must not be provisioned");
			},
			List.of(new KakaoOAuthProviderUserInfoExtractor())
		);

		OAuth2AuthenticationException exception = catchThrowableOfType(
			() -> service.loadUser(userRequest("unknown")),
			OAuth2AuthenticationException.class
		);

		assertThat(exception.getError().getDescription())
			.isEqualTo("Unsupported OAuth provider: unknown");
	}

	private OAuth2UserRequest userRequest(String registrationId) {
		return userRequest(registrationId, "id");
	}

	private OAuth2UserRequest userRequest(String registrationId, String userNameAttributeName) {
		ClientRegistration registration = ClientRegistration
			.withRegistrationId(registrationId)
			.clientId("client-id")
			.clientSecret("client-secret")
			.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
			.redirectUri("http://localhost/login/oauth2/code/" + registrationId)
			.authorizationUri("https://example.com/oauth/authorize")
			.tokenUri("https://example.com/oauth/token")
			.userInfoUri("https://example.com/userinfo")
			.userNameAttributeName(userNameAttributeName)
			.build();
		return new OAuth2UserRequest(registration, accessToken());
	}

	private OAuth2AccessToken accessToken() {
		return new OAuth2AccessToken(
			OAuth2AccessToken.TokenType.BEARER,
			"token",
			null,
			null
		);
	}
}
