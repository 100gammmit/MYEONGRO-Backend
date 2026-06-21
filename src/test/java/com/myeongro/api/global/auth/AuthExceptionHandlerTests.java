package com.myeongro.api.global.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.myeongro.api.global.auth.exception.InvalidAuthenticatedUserException;
import com.myeongro.api.global.auth.exception.UnauthenticatedUserException;

class AuthExceptionHandlerTests {

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders
			.standaloneSetup(new TestController())
			.setControllerAdvice(new AuthExceptionHandler())
			.build();
	}

	@Test
	void mapsMissingAuthenticatedUserToUnauthorized() throws Exception {
		mockMvc.perform(get("/test/missing-user"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error").exists());
	}

	@Test
	void mapsInvalidAuthenticatedUserSubjectToUnauthorized() throws Exception {
		mockMvc.perform(get("/test/invalid-user"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error").exists());
	}

	@RestController
	private static class TestController {

		@GetMapping("/test/missing-user")
		String missingUser() {
			throw new UnauthenticatedUserException();
		}

		@GetMapping("/test/invalid-user")
		String invalidUser() {
			throw new InvalidAuthenticatedUserException(
				new IllegalArgumentException("not a uuid")
			);
		}
	}
}
