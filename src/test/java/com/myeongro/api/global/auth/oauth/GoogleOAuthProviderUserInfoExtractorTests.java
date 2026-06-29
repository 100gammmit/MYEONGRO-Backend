package com.myeongro.api.global.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

class GoogleOAuthProviderUserInfoExtractorTests {

	private final GoogleOAuthProviderUserInfoExtractor extractor =
		new GoogleOAuthProviderUserInfoExtractor();

	@Test
	void extractsGoogleUserInfo() {
		OAuthProviderUserInfo userInfo = extractor.extract(Map.of(
			"sub", "google-user-1",
			"name", "Myeongro User",
			"email", "user@example.com"
		));

		assertThat(userInfo.provider()).isEqualTo("google");
		assertThat(userInfo.providerUserId()).isEqualTo("google-user-1");
		assertThat(userInfo.displayName()).isEqualTo("Myeongro User");
		assertThat(userInfo.email()).isEqualTo("user@example.com");
	}

	@Test
	void rejectsGoogleUserInfoWithoutSubject() {
		OAuth2AuthenticationException exception = catchThrowableOfType(
			() -> extractor.extract(Map.of("email", "user@example.com")),
			OAuth2AuthenticationException.class
		);

		assertThat(exception.getError().getDescription())
			.isEqualTo("Google user subject is missing");
	}
}
