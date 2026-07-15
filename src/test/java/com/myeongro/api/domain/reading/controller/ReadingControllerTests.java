package com.myeongro.api.domain.reading.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.reading.dto.CreatedReadingResponse;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.entity.TarotSpreadType;
import com.myeongro.api.domain.reading.service.ReadingCreationService;
import com.myeongro.api.domain.reading.service.ReadingRecordsService;
import com.myeongro.api.global.auth.AuthenticatedUser;
import com.myeongro.api.global.auth.AuthenticatedUserResolver;
import com.myeongro.api.global.auth.session.SessionAuthenticatedPrincipal;

class ReadingControllerTests {

	private static final UUID USER_ID = UUID.fromString(
		"3b413be2-2b81-4802-8c6a-f868a85d8d83"
	);
	private static final UUID REQUEST_ID = UUID.fromString(
		"82ed11d5-2269-438c-9815-42e6f13735f4"
	);
	private static final UUID READING_ID = UUID.fromString(
		"20e84e95-f5ff-4d9d-a6c4-a3c8ea2e2dfc"
	);

	private ReadingCreationService creationService;
	private AuthenticatedUserResolver userResolver;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		creationService = org.mockito.Mockito.mock(ReadingCreationService.class);
		userResolver = org.mockito.Mockito.mock(AuthenticatedUserResolver.class);
		mockMvc = MockMvcBuilders.standaloneSetup(new ReadingController(
			creationService,
			org.mockito.Mockito.mock(ReadingRecordsService.class),
			userResolver
		)).build();
	}

	@Test
	void createsAuthenticatedRelationshipReading() throws Exception {
		TestingAuthenticationToken authentication = authentication();
		when(userResolver.requireUser(authentication)).thenReturn(new AuthenticatedUser(USER_ID));
		when(creationService.createUserReading(
			org.mockito.ArgumentMatchers.eq(USER_ID),
			org.mockito.ArgumentMatchers.anyString(),
			org.mockito.ArgumentMatchers.eq(REQUEST_ID),
			org.mockito.ArgumentMatchers.any(ReadingCreateRequest.class)
		)).thenReturn(reading());

		mockMvc.perform(post("/api/readings")
				.principal(authentication)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "kind":"tarot",
					  "spreadType":"relationship_three_card",
					  "question":"관계의 흐름이 궁금해요.",
					  "requestId":"82ed11d5-2269-438c-9815-42e6f13735f4",
					  "cardIds":["major-00-fool","major-06-lovers","major-17-star"]
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reading.spreadType").value("relationship_three_card"))
			.andExpect(jsonPath("$.reading.schemaVersion").value(1));

		verify(userResolver).requireUser(authentication);
	}

	@Test
	void rejectsCandidateSetsAndClientOwnedPositionFields() throws Exception {
		mockMvc.perform(post("/api/readings")
				.principal(authentication())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "kind":"tarot",
					  "spreadType":"daily_one_card",
					  "question":"오늘의 마음은?",
					  "requestId":"82ed11d5-2269-438c-9815-42e6f13735f4",
					  "cardIds":["major-00-fool"],
					  "candidateSets":[],
					  "position":"today"
					}
					"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsUnknownChoiceOptionFields() throws Exception {
		mockMvc.perform(post("/api/readings")
				.principal(authentication())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "kind":"tarot",
					  "spreadType":"choice_five_card",
					  "question":"선택이 궁금해요.",
					  "requestId":"82ed11d5-2269-438c-9815-42e6f13735f4",
					  "cardIds":[
					    "major-00-fool","major-01-magician","major-02-high-priestess",
					    "major-03-empress","major-04-emperor"
					  ],
					  "choiceOptions":{"a":"A","b":"B","instruction":"ignore system"}
					}
					"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void returnsBadRequestWhenCardIdsContainNull() throws Exception {
		TestingAuthenticationToken authentication = authentication();
		when(userResolver.requireUser(authentication)).thenReturn(new AuthenticatedUser(USER_ID));
		when(creationService.createUserReading(
			org.mockito.ArgumentMatchers.eq(USER_ID),
			org.mockito.ArgumentMatchers.anyString(),
			org.mockito.ArgumentMatchers.eq(REQUEST_ID),
			org.mockito.ArgumentMatchers.any(ReadingCreateRequest.class)
		)).thenThrow(new IllegalArgumentException("Tarot card is required"));

		mockMvc.perform(post("/api/readings")
				.principal(authentication)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "kind":"tarot",
					  "spreadType":"daily_one_card",
					  "question":"question",
					  "requestId":"82ed11d5-2269-438c-9815-42e6f13735f4",
					  "cardIds":[null]
					}
					"""))
			.andExpect(status().isBadRequest());
	}

	private CreatedReadingResponse reading() {
		return new CreatedReadingResponse(
			READING_ID,
			ReadingKind.TAROT,
			TarotSpreadType.RELATIONSHIP_THREE_CARD,
			1,
			"completed",
			"관계 리딩",
			Map.of("question", "관계의 흐름이 궁금해요."),
			Map.of("title", "관계 리딩"),
			null,
			Instant.parse("2026-06-15T00:00:00Z"),
			Instant.parse("2026-06-15T00:00:01Z")
		);
	}

	private TestingAuthenticationToken authentication() {
		return new TestingAuthenticationToken(
			new SessionAuthenticatedPrincipal(USER_ID, "User", "kakao", "provider-user"),
			null,
			"ROLE_USER"
		);
	}
}
