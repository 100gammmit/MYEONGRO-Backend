package com.myeongro.api.global.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class OAuthScopeConfigurationTests {

	@Test
	void requestsOnlyTheIdentifierScopesNeededForLogin() throws IOException {
		String yaml = Files.readString(
			Path.of("src", "main", "resources", "application.yaml")
		).replace("\r\n", "\n");

		assertThat(yaml)
			.contains("scope:\n              - openid")
			.doesNotContain("profile_nickname")
			.doesNotContain("              - profile\n")
			.doesNotContain("              - email\n");
	}
}
