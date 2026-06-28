package com.myeongro.api.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.myeongro.api.global.auth.oauth.OAuth2SessionUserService;
import com.myeongro.testsupport.SecurityConfigTestEndpoint;

@WebMvcTest(controllers = SecurityConfigTestEndpoint.class)
@Import({ SecurityConfig.class, SecurityConfigTestEndpoint.class })
@TestPropertySource(properties = {
	"app.frontend-origin=http://localhost:3000",
	"spring.security.oauth2.client.registration.kakao.client-id=test-client-id",
	"spring.security.oauth2.client.registration.kakao.client-secret=test-client-secret",
	"spring.security.oauth2.client.registration.kakao.redirect-uri=http://localhost/login/oauth2/code/kakao",
	"spring.security.oauth2.client.registration.kakao.authorization-grant-type=authorization_code",
	"spring.security.oauth2.client.registration.kakao.client-authentication-method=client_secret_post",
	"spring.security.oauth2.client.registration.kakao.scope=profile_nickname",
	"spring.security.oauth2.client.provider.kakao.authorization-uri=https://kauth.kakao.com/oauth/authorize",
	"spring.security.oauth2.client.provider.kakao.token-uri=https://kauth.kakao.com/oauth/token",
	"spring.security.oauth2.client.provider.kakao.user-info-uri=https://kapi.kakao.com/v2/user/me",
	"spring.security.oauth2.client.provider.kakao.user-name-attribute=id"
})
class SecurityConfigTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private OAuth2SessionUserService oauth2SessionUserService;

	@Test
	void allowsGuestReadingCreationEndpoint() throws Exception {
		mockMvc.perform(post("/api/readings"))
			.andExpect(status().isOk());
	}

	@Test
	void requiresAuthenticationForReadingRecordsEndpoint() throws Exception {
		mockMvc.perform(get("/api/readings"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void requiresAuthenticationForUnlistedEndpointByDefault() throws Exception {
		mockMvc.perform(get("/internal/security-test"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void allowsSessionAuthenticatedUnlistedEndpointByDefault() throws Exception {
		mockMvc.perform(get("/internal/security-test")
				.with(user("session-user")))
			.andExpect(status().isOk());
	}

	@Test
	void allowsCurrentUserEndpointWithoutSession() throws Exception {
		mockMvc.perform(get("/api/auth/me"))
			.andExpect(status().isOk());
	}

	@Test
	void allowsLogoutEndpointWithoutSession() throws Exception {
		mockMvc.perform(post("/api/auth/logout"))
			.andExpect(status().isNoContent());
	}
}
