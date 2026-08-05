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
import com.myeongro.api.domain.reading.exception.InvalidReadingRequestException;
import com.myeongro.api.global.auth.AuthenticatedUser;
import com.myeongro.api.global.auth.AuthenticatedUserResolver;
import com.myeongro.api.global.auth.session.SessionAuthenticatedPrincipal;
import com.myeongro.api.domain.tarotdraw.controller.TarotDrawSessionExceptionHandler;
import com.myeongro.api.domain.tarotdraw.exception.TarotDrawSessionException;
import com.myeongro.api.domain.saju.calculation.SajuCalculationException;

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
		)).setControllerAdvice(new TarotDrawSessionExceptionHandler()).build();
	}

	@Test
	void createsAuthenticatedRelationshipReading() throws Exception {
		TestingAuthenticationToken authentication = authentication();
		when(userResolver.requireUser(authentication)).thenReturn(new AuthenticatedUser(USER_ID));
		when(creationService.createReading(
			org.mockito.ArgumentMatchers.eq(USER_ID),
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
					  "drawSessionId":"draw-session-id"
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
					  "drawSessionId":"draw-session-id",
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
					  "drawSessionId":"draw-session-id",
					  "choiceOptions":{"a":"A","b":"B","instruction":"ignore system"}
					}
					"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsClientOwnedCardIds() throws Exception {
		mockMvc.perform(post("/api/readings")
				.principal(authentication())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "kind":"tarot",
					  "spreadType":"daily_one_card",
					  "question":"question",
					  "requestId":"82ed11d5-2269-438c-9815-42e6f13735f4",
					  "drawSessionId":"draw-session-id",
					  "cardIds":["major-00-fool"]
					}
					"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void returnsStableCalculationErrorWithoutExposingEngineFailure() throws Exception {
		TestingAuthenticationToken authentication = authentication();
		when(userResolver.requireUser(authentication)).thenReturn(new AuthenticatedUser(USER_ID));
		when(creationService.createReading(
			org.mockito.ArgumentMatchers.eq(USER_ID),
			org.mockito.ArgumentMatchers.eq(REQUEST_ID),
			org.mockito.ArgumentMatchers.any(ReadingCreateRequest.class)
		)).thenThrow(new SajuCalculationException(new IllegalStateException("secret engine detail")));

		mockMvc.perform(post("/api/readings")
				.principal(authentication)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "kind":"saju",
					  "question":"올해 흐름이 궁금해요",
					  "requestId":"82ed11d5-2269-438c-9815-42e6f13735f4",
					  "focusArea":"career",
					  "birthProfile":{
					    "calendarType":"solar",
					    "birthDate":"1992-08-17",
					    "birthTimePrecision":"unknown",
					    "provinceCode":"36",
					    "cityCode":"36110",
					    "luckDirectionBasis":"unspecified"
					  }
					}
					"""))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("SAJU_CALCULATION_FAILED"))
			.andExpect(jsonPath("$.error").value("사주 계산을 완료하지 못했습니다."));
	}

	@Test
	void returnsStableCodeAndFieldForSajuValidationErrors() throws Exception {
		TestingAuthenticationToken authentication = authentication();
		when(userResolver.requireUser(authentication)).thenReturn(new AuthenticatedUser(USER_ID));
		when(creationService.createReading(
			org.mockito.ArgumentMatchers.eq(USER_ID),
			org.mockito.ArgumentMatchers.eq(REQUEST_ID),
			org.mockito.ArgumentMatchers.any(ReadingCreateRequest.class)
		)).thenThrow(new InvalidReadingRequestException(
			"INVALID_BIRTH_TIME",
			"birthProfile.birthTime",
			"출생시간을 확인해 주세요."
		));

		mockMvc.perform(post("/api/readings")
				.principal(authentication)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "kind":"saju",
					  "question":"올해 이직운이 궁금해요",
					  "requestId":"82ed11d5-2269-438c-9815-42e6f13735f4",
					  "focusArea":"career",
					  "birthProfile":{
					    "calendarType":"solar",
					    "birthDate":"1992-08-17",
					    "birthTime":"25:00",
					    "birthTimePrecision":"exact",
					    "provinceCode":"11",
					    "cityCode":"11680",
					    "luckDirectionBasis":"female"
					  }
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_BIRTH_TIME"))
			.andExpect(jsonPath("$.field").value("birthProfile.birthTime"))
			.andExpect(jsonPath("$.message").value("출생시간을 확인해 주세요."));
	}

	@Test
	void rejectsUnknownNestedSajuFieldsWithTheirPath() throws Exception {
		mockMvc.perform(post("/api/readings")
				.principal(authentication())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "kind":"saju",
					  "question":"질문",
					  "requestId":"82ed11d5-2269-438c-9815-42e6f13735f4",
					  "focusArea":"career",
					  "birthProfile":{
					    "calendarType":"solar",
					    "birthDate":"1992-08-17",
					    "birthTimePrecision":"unknown",
					    "provinceCode":"11",
					    "cityCode":"11680",
					    "luckDirectionBasis":"unspecified",
					    "pillars":{}
					  }
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"))
			.andExpect(jsonPath("$.field").value("birthProfile.pillars"));
	}

	@Test
	void returnsStableDrawSessionErrorFromReadingCreation() throws Exception {
		TestingAuthenticationToken authentication = authentication();
		when(userResolver.requireUser(authentication)).thenReturn(new AuthenticatedUser(USER_ID));
		when(creationService.createReading(
			org.mockito.ArgumentMatchers.eq(USER_ID),
			org.mockito.ArgumentMatchers.eq(REQUEST_ID),
			org.mockito.ArgumentMatchers.any(ReadingCreateRequest.class)
		)).thenThrow(TarotDrawSessionException.alreadyConsumed());

		mockMvc.perform(post("/api/readings")
				.principal(authentication)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "kind":"tarot",
					  "spreadType":"daily_one_card",
					  "question":"question",
					  "requestId":"82ed11d5-2269-438c-9815-42e6f13735f4",
					  "drawSessionId":"draw-session-id"
					}
					"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DRAW_SESSION_ALREADY_CONSUMED"));
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
