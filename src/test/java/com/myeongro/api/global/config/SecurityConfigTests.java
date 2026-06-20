package com.myeongro.api.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.myeongro.testsupport.SecurityConfigTestEndpoint;

@WebMvcTest(controllers = SecurityConfigTestEndpoint.class)
@Import({ SecurityConfig.class, SecurityConfigTestEndpoint.class })
@TestPropertySource(properties = {
	"app.auth.issuer-uri=https://project.supabase.co/auth/v1",
	"app.auth.jwk-set-uri=https://project.supabase.co/auth/v1/.well-known/jwks.json"
})
class SecurityConfigTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private JwtDecoder jwtDecoder;

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
	void allowsAuthenticatedUnlistedEndpointByDefault() throws Exception {
		mockMvc.perform(get("/internal/security-test")
				.with(jwt().jwt(jwt -> jwt.subject("43bc72f9-eed1-4e4b-8717-6fe969b4ea43"))))
			.andExpect(status().isOk());
	}
}
