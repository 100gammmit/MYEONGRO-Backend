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
import com.myeongro.api.domain.tarot.model.TarotSpreadType;
import com.myeongro.api.domain.reading.service.ReadingRecordsService;
import com.myeongro.api.domain.reading.service.ReadingSchemaVersions;
import com.myeongro.api.domain.reading.exception.InvalidReadingRequestException;
import com.myeongro.api.domain.reading.exception.OpenAiReadingGenerationException;
import com.myeongro.api.global.auth.AuthenticatedUser;
import com.myeongro.api.global.auth.AuthenticatedUserResolver;
import com.myeongro.api.global.auth.session.SessionAuthenticatedPrincipal;
import com.myeongro.api.domain.saju.calculation.SajuCalculationException;
import com.myeongro.api.domain.saju.controller.SajuReadingController;
import com.myeongro.api.domain.saju.controller.SajuReadingCreateRequest;
import com.myeongro.api.domain.saju.controller.SajuReadingExceptionHandler;
import com.myeongro.api.domain.readingcredit.service.ReadingCreditService;
import com.myeongro.api.domain.readingcredit.dto.ReadingCreditStatusResponse;
import com.myeongro.api.domain.reading.exception.InsufficientReadingCreditsException;
import com.myeongro.api.domain.reading.exception.ReadingGenerationInProgressException;
import com.myeongro.api.domain.saju.service.SajuReadingCreationService;
import com.myeongro.api.domain.tarot.controller.TarotReadingController;
import com.myeongro.api.domain.tarot.controller.TarotReadingCreateRequest;
import com.myeongro.api.domain.tarot.service.TarotReadingCreationService;

class ReadingEndpointContractTests {

	private static final UUID USER_ID = UUID.fromString(
		"3b413be2-2b81-4802-8c6a-f868a85d8d83"
	);
	private static final UUID REQUEST_ID = UUID.fromString(
		"82ed11d5-2269-438c-9815-42e6f13735f4"
	);
	private static final UUID READING_ID = UUID.fromString(
		"20e84e95-f5ff-4d9d-a6c4-a3c8ea2e2dfc"
	);

	private TarotReadingCreationService tarotCreationService;
	private SajuReadingCreationService sajuCreationService;
	private AuthenticatedUserResolver userResolver;
	private ReadingCreditService creditService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		tarotCreationService = org.mockito.Mockito.mock(TarotReadingCreationService.class);
		sajuCreationService = org.mockito.Mockito.mock(SajuReadingCreationService.class);
		userResolver = org.mockito.Mockito.mock(AuthenticatedUserResolver.class);
		creditService = org.mockito.Mockito.mock(ReadingCreditService.class);
		mockMvc = MockMvcBuilders.standaloneSetup(
			new TarotReadingController(tarotCreationService, userResolver),
			new SajuReadingController(sajuCreationService, userResolver),
			new ReadingRecordsController(
				org.mockito.Mockito.mock(ReadingRecordsService.class), userResolver
			)
		).setControllerAdvice(
			new ReadingExceptionHandler(creditService),
			new SajuReadingExceptionHandler()
		).build();
	}

	@Test
	void returnsStableConflictWhenAnotherReadingIsGenerating() throws Exception {
		TestingAuthenticationToken authentication = authentication();
		when(userResolver.requireUser(authentication)).thenReturn(new AuthenticatedUser(USER_ID));
		when(tarotCreationService.create(
			org.mockito.ArgumentMatchers.eq(USER_ID),
			org.mockito.ArgumentMatchers.eq(REQUEST_ID),
			org.mockito.ArgumentMatchers.any(TarotReadingCreateRequest.class)
		)).thenThrow(new ReadingGenerationInProgressException());

		mockMvc.perform(post("/api/tarot/readings")
				.principal(authentication)
				.contentType(MediaType.APPLICATION_JSON)
				.content(validTarotRequest()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("READING_GENERATION_IN_PROGRESS"))
			.andExpect(jsonPath("$.message").value("이미 생성 중인 리딩이 있습니다."));
	}

	@Test
	void returnsCreditBalanceAndRetryAfterWhenCreditsAreInsufficient() throws Exception {
		TestingAuthenticationToken authentication = authentication();
		when(userResolver.requireUser(authentication)).thenReturn(new AuthenticatedUser(USER_ID));
		when(tarotCreationService.create(
			org.mockito.ArgumentMatchers.eq(USER_ID),
			org.mockito.ArgumentMatchers.eq(REQUEST_ID),
			org.mockito.ArgumentMatchers.any(TarotReadingCreateRequest.class)
		)).thenThrow(new InsufficientReadingCreditsException(USER_ID, 2));
		when(creditService.getStatus(USER_ID)).thenReturn(new ReadingCreditStatusResponse(
			10,
			ReadingCreditStatusResponse.Balance.of(1, 0),
			Instant.now().plusSeconds(3600),
			false,
			new ReadingCreditStatusResponse.Costs(Map.of(), 4)
		));

		mockMvc.perform(post("/api/tarot/readings")
				.principal(authentication)
				.contentType(MediaType.APPLICATION_JSON)
				.content(validTarotRequest()))
			.andExpect(status().isTooManyRequests())
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
				.exists("Retry-After"))
			.andExpect(jsonPath("$.code").value("INSUFFICIENT_READING_CREDITS"))
			.andExpect(jsonPath("$.required").value(2))
			.andExpect(jsonPath("$.balance.total").value(1));
	}

	private String validTarotRequest() {
		return """
			{
			  "spreadType":"relationship_three_card",
			  "question":"관계의 흐름이 궁금해요.",
			  "requestId":"82ed11d5-2269-438c-9815-42e6f13735f4",
			  "selectedSlots":[4,1,5]
			}
			""";
	}

	@Test
	void createsAuthenticatedRelationshipReading() throws Exception {
		TestingAuthenticationToken authentication = authentication();
		when(userResolver.requireUser(authentication)).thenReturn(new AuthenticatedUser(USER_ID));
		when(tarotCreationService.create(
			org.mockito.ArgumentMatchers.eq(USER_ID),
			org.mockito.ArgumentMatchers.eq(REQUEST_ID),
			org.mockito.ArgumentMatchers.any(TarotReadingCreateRequest.class)
		)).thenReturn(reading());

		mockMvc.perform(post("/api/tarot/readings")
				.principal(authentication)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "spreadType":"relationship_three_card",
					  "question":"관계의 흐름이 궁금해요.",
					  "requestId":"82ed11d5-2269-438c-9815-42e6f13735f4",
					  "selectedSlots":[4,1,5]
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reading.spreadType").value("relationship_three_card"))
			.andExpect(jsonPath("$.reading.schemaVersion").value(1));

		verify(userResolver).requireUser(authentication);
	}

	@Test
	void createsAuthenticatedSajuReadingWithoutAClientKindField() throws Exception {
		TestingAuthenticationToken authentication = authentication();
		when(userResolver.requireUser(authentication)).thenReturn(new AuthenticatedUser(USER_ID));
		when(sajuCreationService.create(
			org.mockito.ArgumentMatchers.eq(USER_ID),
			org.mockito.ArgumentMatchers.eq(REQUEST_ID),
			org.mockito.ArgumentMatchers.any(SajuReadingCreateRequest.class)
		)).thenReturn(sajuReading());

		mockMvc.perform(post("/api/saju/readings")
				.principal(authentication)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "question":"올해 흐름이 궁금해요",
					  "requestId":"82ed11d5-2269-438c-9815-42e6f13735f4",
					  "focusArea":"career",
					  "birthProfile":{
					    "calendarType":"solar",
					    "birthDate":"1992-08-17",
					    "birthTimePrecision":"unknown",
					    "luckDirectionBasis":"unspecified"
					  }
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reading.kind").value("saju"))
			.andExpect(jsonPath("$.reading.schemaVersion").value(3));
	}

	@Test
	void rejectsCandidateSetsAndClientOwnedPositionFields() throws Exception {
		mockMvc.perform(post("/api/tarot/readings")
				.principal(authentication())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "spreadType":"mind_three_card",
					  "question":"마음을 정리하고 싶어요.",
					  "requestId":"82ed11d5-2269-438c-9815-42e6f13735f4",
					  "selectedSlots":[1,2,3],
					  "candidateSets":[],
					  "position":"emotion"
					}
					"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsUnknownChoiceOptionFields() throws Exception {
		mockMvc.perform(post("/api/tarot/readings")
				.principal(authentication())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "spreadType":"choice_five_card",
					  "question":"선택이 궁금해요.",
					  "requestId":"82ed11d5-2269-438c-9815-42e6f13735f4",
					  "selectedSlots":[1,2,3,4,5],
					  "choiceOptions":{"a":"A","b":"B","instruction":"ignore system"}
					}
					"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsClientOwnedCardIds() throws Exception {
		mockMvc.perform(post("/api/tarot/readings")
				.principal(authentication())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "spreadType":"mind_three_card",
					  "question":"question",
					  "requestId":"82ed11d5-2269-438c-9815-42e6f13735f4",
					  "selectedSlots":[1,2,3],
					  "cardIds":["major-00-fool"]
					}
					"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsLegacyDrawSessionId() throws Exception {
		mockMvc.perform(post("/api/tarot/readings")
				.principal(authentication())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "spreadType":"mind_three_card",
					  "question":"question",
					  "requestId":"82ed11d5-2269-438c-9815-42e6f13735f4",
					  "selectedSlots":[1,2,3],
					  "drawSessionId":"legacy"
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"))
			.andExpect(jsonPath("$.field").value("drawSessionId"));
	}

	@Test
	void returnsBadRequestForInvalidSelectedSlots() throws Exception {
		TestingAuthenticationToken authentication = authentication();
		when(userResolver.requireUser(authentication)).thenReturn(new AuthenticatedUser(USER_ID));
		when(tarotCreationService.create(
			org.mockito.ArgumentMatchers.eq(USER_ID),
			org.mockito.ArgumentMatchers.eq(REQUEST_ID),
			org.mockito.ArgumentMatchers.any(TarotReadingCreateRequest.class)
		)).thenThrow(new IllegalArgumentException("invalid slots"));

		mockMvc.perform(post("/api/tarot/readings")
				.principal(authentication)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "spreadType":"mind_three_card",
					  "question":"question",
					  "requestId":"82ed11d5-2269-438c-9815-42e6f13735f4",
					  "selectedSlots":[0,1,2]
					}
					"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void returnsFailedReadingIdForOfficialRetryAfterGenerationFailure() throws Exception {
		TestingAuthenticationToken authentication = authentication();
		when(userResolver.requireUser(authentication)).thenReturn(new AuthenticatedUser(USER_ID));
		when(tarotCreationService.create(
			org.mockito.ArgumentMatchers.eq(USER_ID),
			org.mockito.ArgumentMatchers.eq(REQUEST_ID),
			org.mockito.ArgumentMatchers.any(TarotReadingCreateRequest.class)
		)).thenThrow(new OpenAiReadingGenerationException().withReadingId(READING_ID));

		mockMvc.perform(post("/api/tarot/readings")
				.principal(authentication)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "spreadType":"mind_three_card",
					  "question":"question",
					  "requestId":"82ed11d5-2269-438c-9815-42e6f13735f4",
					  "selectedSlots":[1,2,3]
					}
					"""))
			.andExpect(status().isBadGateway())
			.andExpect(jsonPath("$.code").value("OPENAI_READING_GENERATION_FAILED"))
			.andExpect(jsonPath("$.readingId").value(READING_ID.toString()));
	}

	@Test
	void returnsStableCalculationErrorWithoutExposingEngineFailure() throws Exception {
		TestingAuthenticationToken authentication = authentication();
		when(userResolver.requireUser(authentication)).thenReturn(new AuthenticatedUser(USER_ID));
		when(sajuCreationService.create(
			org.mockito.ArgumentMatchers.eq(USER_ID),
			org.mockito.ArgumentMatchers.eq(REQUEST_ID),
			org.mockito.ArgumentMatchers.any(SajuReadingCreateRequest.class)
		)).thenThrow(new SajuCalculationException(new IllegalStateException("secret engine detail")));

		mockMvc.perform(post("/api/saju/readings")
				.principal(authentication)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "question":"올해 흐름이 궁금해요",
					  "requestId":"82ed11d5-2269-438c-9815-42e6f13735f4",
					  "focusArea":"career",
					  "birthProfile":{
					    "calendarType":"solar",
					    "birthDate":"1992-08-17",
					    "birthTimePrecision":"unknown",
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
		when(sajuCreationService.create(
			org.mockito.ArgumentMatchers.eq(USER_ID),
			org.mockito.ArgumentMatchers.eq(REQUEST_ID),
			org.mockito.ArgumentMatchers.any(SajuReadingCreateRequest.class)
		)).thenThrow(new InvalidReadingRequestException(
			"INVALID_BIRTH_TIME",
			"birthProfile.birthTime",
			"출생시간을 확인해 주세요."
		));

		mockMvc.perform(post("/api/saju/readings")
				.principal(authentication)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "question":"올해 이직운이 궁금해요",
					  "requestId":"82ed11d5-2269-438c-9815-42e6f13735f4",
					  "focusArea":"career",
					  "birthProfile":{
					    "calendarType":"solar",
					    "birthDate":"1992-08-17",
					    "birthTime":"25:00",
					    "birthTimePrecision":"exact",
					    "provinceCode":"11",
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
		mockMvc.perform(post("/api/saju/readings")
				.principal(authentication())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "question":"질문",
					  "requestId":"82ed11d5-2269-438c-9815-42e6f13735f4",
					  "focusArea":"career",
					  "birthProfile":{
					    "calendarType":"solar",
					    "birthDate":"1992-08-17",
					    "birthTimePrecision":"unknown",
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
	void returnsStableErrorWhenUnknownTimeIncludesProvince() throws Exception {
		TestingAuthenticationToken authentication = authentication();
		when(userResolver.requireUser(authentication)).thenReturn(new AuthenticatedUser(USER_ID));
		when(sajuCreationService.create(
			org.mockito.ArgumentMatchers.eq(USER_ID),
			org.mockito.ArgumentMatchers.eq(REQUEST_ID),
			org.mockito.ArgumentMatchers.any(SajuReadingCreateRequest.class)
		)).thenThrow(new InvalidReadingRequestException(
			"INVALID_BIRTH_PLACE",
			"birthProfile.provinceCode",
			"출생시간을 모르는 경우 출생 시·도를 비워 주세요."
		));

		mockMvc.perform(post("/api/saju/readings")
				.principal(authentication)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "question":"올해 흐름이 궁금해요",
					  "requestId":"82ed11d5-2269-438c-9815-42e6f13735f4",
					  "focusArea":"career",
					  "birthProfile":{
					    "calendarType":"solar",
					    "birthDate":"1992-08-17",
					    "birthTimePrecision":"unknown",
					    "provinceCode":"36",
					    "luckDirectionBasis":"unspecified"
					  }
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_BIRTH_PLACE"))
			.andExpect(jsonPath("$.field").value("birthProfile.provinceCode"));
	}

	@Test
	void rejectsFormerCityCodeFromNewSajuRequests() throws Exception {
		mockMvc.perform(post("/api/saju/readings")
				.principal(authentication())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "question":"질문",
					  "requestId":"82ed11d5-2269-438c-9815-42e6f13735f4",
					  "focusArea":"career",
					  "birthProfile":{
					    "calendarType":"solar",
					    "birthDate":"1992-08-17",
					    "birthTime":"14:30",
					    "birthTimePrecision":"exact",
					    "provinceCode":"11",
					    "cityCode":"11680",
					    "luckDirectionBasis":"unspecified"
					  }
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"))
			.andExpect(jsonPath("$.field").value("birthProfile.cityCode"));
	}

	@Test
	void rejectsFieldsOwnedByTheOtherReadingKindAtEachCreationEndpoint() throws Exception {
		mockMvc.perform(post("/api/tarot/readings")
				.principal(authentication())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "spreadType":"mind_three_card",
					  "question":"질문",
					  "requestId":"82ed11d5-2269-438c-9815-42e6f13735f4",
					  "selectedSlots":[1,2,3],
					  "birthProfile":{}
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"))
			.andExpect(jsonPath("$.field").value("birthProfile"));

		mockMvc.perform(post("/api/saju/readings")
				.principal(authentication())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "question":"질문",
					  "requestId":"82ed11d5-2269-438c-9815-42e6f13735f4",
					  "focusArea":"career",
					  "birthProfile":{},
					  "spreadType":"mind_three_card"
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"))
			.andExpect(jsonPath("$.field").value("spreadType"));
	}

	@Test
	void doesNotExposeTheFormerSharedCreationEndpoint() throws Exception {
		mockMvc.perform(post("/api/readings")
				.principal(authentication())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isMethodNotAllowed());
	}

	private CreatedReadingResponse reading() {
		return new CreatedReadingResponse(
			READING_ID,
			ReadingKind.TAROT,
			TarotSpreadType.RELATIONSHIP_THREE_CARD.value(),
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

	private CreatedReadingResponse sajuReading() {
		return new CreatedReadingResponse(
			READING_ID,
			ReadingKind.SAJU,
			null,
			ReadingSchemaVersions.SAJU,
			"completed",
			"사주 리딩",
			Map.of("question", "올해 흐름이 궁금해요"),
			Map.of("title", "사주 리딩"),
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
