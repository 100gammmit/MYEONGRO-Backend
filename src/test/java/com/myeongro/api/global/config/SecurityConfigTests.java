package com.myeongro.api.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = SecurityConfigTests.TestEndpoint.class)
@Import(SecurityConfig.class)
class SecurityConfigTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void rejectsUnlistedEndpointByDefault() throws Exception {
		mockMvc.perform(get("/internal/security-test"))
			.andExpect(status().isForbidden());
	}

	@RestController
	static class TestEndpoint {

		@GetMapping("/internal/security-test")
		String secure() {
			return "ok";
		}
	}
}
