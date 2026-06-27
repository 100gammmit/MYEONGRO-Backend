package com.myeongro.api.global.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.myeongro.api.global.auth.session.SessionAuthenticatedPrincipal;

class AuthControllerTests {

	private static final UUID USER_ID =
		UUID.fromString("43bc72f9-eed1-4e4b-8717-6fe969b4ea43");

	private AuthenticatedUserResolver userResolver;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		userResolver = org.mockito.Mockito.mock(AuthenticatedUserResolver.class);
		mockMvc = MockMvcBuilders
			.standaloneSetup(new AuthController(userResolver))
			.build();
	}

	@Test
	void meReturnsGuestShapeWhenSessionPrincipalIsMissing() throws Exception {
		mockMvc.perform(get("/auth/me"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.authenticated").value(false));
	}

	@Test
	void meReturnsSessionUser() throws Exception {
		TestingAuthenticationToken authentication = authentication();
		when(userResolver.requireUser(authentication)).thenReturn(new AuthenticatedUser(USER_ID));

		mockMvc.perform(get("/auth/me").principal(authentication))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.authenticated").value(true))
			.andExpect(jsonPath("$.user.id").value(USER_ID.toString()))
			.andExpect(jsonPath("$.user.displayName").value("명로 사용자"));
	}

	@Test
	void logoutInvalidatesSession() throws Exception {
		MockHttpSession session = new MockHttpSession();

		mockMvc.perform(post("/auth/logout").session(session))
			.andExpect(status().isNoContent());

		assertThat(session.isInvalid()).isTrue();
	}

	private TestingAuthenticationToken authentication() {
		SessionAuthenticatedPrincipal principal = new SessionAuthenticatedPrincipal(
			USER_ID,
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
