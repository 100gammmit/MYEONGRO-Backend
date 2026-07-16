package com.myeongro.api.domain.tarotdraw.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.myeongro.api.domain.tarotdraw.exception.TarotDrawSessionException;
import com.myeongro.api.domain.tarotdraw.service.TarotDrawCandidateView;
import com.myeongro.api.domain.tarotdraw.service.TarotDrawSessionService;
import com.myeongro.api.domain.tarotdraw.service.TarotDrawSessionView;
import com.myeongro.api.global.auth.AuthenticatedUser;
import com.myeongro.api.global.auth.AuthenticatedUserResolver;

class TarotDrawSessionControllerTests {

	private static final UUID USER_ID = UUID.fromString("3b413be2-2b81-4802-8c6a-f868a85d8d83");
	private TarotDrawSessionService service;
	private AuthenticatedUserResolver resolver;
	private MockMvc mockMvc;
	private TestingAuthenticationToken authentication;

	@BeforeEach
	void setUp() {
		service = org.mockito.Mockito.mock(TarotDrawSessionService.class);
		resolver = org.mockito.Mockito.mock(AuthenticatedUserResolver.class);
		authentication = new TestingAuthenticationToken("user", null, "ROLE_USER");
		when(resolver.requireUser(authentication)).thenReturn(new AuthenticatedUser(USER_ID));
		mockMvc = MockMvcBuilders.standaloneSetup(
			new TarotDrawSessionController(service, resolver)
		).setControllerAdvice(new TarotDrawSessionExceptionHandler()).build();
	}

	@Test
	void inProgressResponseNeverSerializesCanonicalCardIds() throws Exception {
		when(service.create(USER_ID, "daily_one_card")).thenReturn(inProgress());

		mockMvc.perform(post("/api/tarot/draw-sessions")
				.principal(authentication)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"spreadType\":\"daily_one_card\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.candidates.length()").value(5))
			.andExpect(jsonPath("$.cards").doesNotExist())
			.andExpect(content().string(org.hamcrest.Matchers.not(
				org.hamcrest.Matchers.containsString("major-")
			)));
	}

	@Test
	void activeAndSelectionInProgressResponsesNeverSerializeCanonicalCardIds() throws Exception {
		TarotDrawSessionView view = inProgress();
		when(service.getActive(USER_ID)).thenReturn(view);
		when(service.select(USER_ID, "session-id", "token-1")).thenReturn(view);

		mockMvc.perform(get("/api/tarot/draw-sessions/active").principal(authentication))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.not(
				org.hamcrest.Matchers.containsString("major-")
			)));
		mockMvc.perform(post("/api/tarot/draw-sessions/session-id/selections")
				.principal(authentication)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"candidateToken\":\"token-1\"}"))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.not(
				org.hamcrest.Matchers.containsString("major-")
			)));
	}

	@Test
	void returnsStableErrorShape() throws Exception {
		when(service.getActive(USER_ID)).thenThrow(TarotDrawSessionException.notFound());

		mockMvc.perform(get("/api/tarot/draw-sessions/active").principal(authentication))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("DRAW_SESSION_NOT_FOUND"))
			.andExpect(jsonPath("$.message").isString());
	}

	private TarotDrawSessionView inProgress() {
		return new TarotDrawSessionView(
			"session-id", "daily_one_card", "in_progress", "today", 0, 1,
			Instant.parse("2026-07-17T00:30:00Z"),
			List.of(
				new TarotDrawCandidateView("token-1"), new TarotDrawCandidateView("token-2"),
				new TarotDrawCandidateView("token-3"), new TarotDrawCandidateView("token-4"),
				new TarotDrawCandidateView("token-5")
			),
			null
		);
	}
}
