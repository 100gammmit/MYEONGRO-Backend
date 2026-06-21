package com.myeongro.api.domain.reading.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.reading.dto.CreatedReadingResponse;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.service.OpenAiReadingGenerationException;
import com.myeongro.api.domain.reading.service.ReadingCreationService;
import com.myeongro.api.domain.reading.service.ReadingRecordNotFoundException;
import com.myeongro.api.domain.reading.service.ReadingRecordsService;
import com.myeongro.api.domain.reading.service.RequiredConsentMissingException;
import com.myeongro.api.global.auth.AuthenticatedUser;
import com.myeongro.api.global.auth.AuthenticatedUserResolver;
import com.myeongro.api.global.cookie.CookieService;
import com.myeongro.api.global.guest.GuestSession;
import com.myeongro.api.global.guest.GuestSessionSigner;

class ReadingControllerTests {

	private static final UUID GUEST_ID =
		UUID.fromString("9775ff70-5708-45d8-85f8-cb57878bc25d");
	private static final UUID REQUEST_ID =
		UUID.fromString("82ed11d5-2269-438c-9815-42e6f13735f4");
	private static final UUID READING_ID =
		UUID.fromString("20e84e95-f5ff-4d9d-a6c4-a3c8ea2e2dfc");
	private static final UUID USER_ID =
		UUID.fromString("3b413be2-2b81-4802-8c6a-f868a85d8d83");

	private ReadingCreationService service;
	private ReadingRecordsService recordsService;
	private GuestSessionSigner signer;
	private AuthenticatedUserResolver userResolver;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		service = mock(ReadingCreationService.class);
		recordsService = mock(ReadingRecordsService.class);
		signer = mock(GuestSessionSigner.class);
		userResolver = mock(AuthenticatedUserResolver.class);
		mockMvc = MockMvcBuilders
			.standaloneSetup(new ReadingController(
				service,
				recordsService,
				signer,
				userResolver
			))
			.setMessageConverters(new MappingJackson2HttpMessageConverter(
				new ObjectMapper().findAndRegisterModules()
			))
			.build();
	}

	@Test
	void rejectsRequestWithoutVerifiedGuestCookie() throws Exception {
		mockMvc.perform(post("/api/readings")
				.contentType("application/json")
				.content(validTarotRequest()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error").exists());

		verifyNoInteractions(service);
	}

	@Test
	void returnsForbiddenWhenRequiredConsentIsMissing() throws Exception {
		when(signer.verify("signed-token")).thenReturn(java.util.Optional.of(
			new GuestSession(GUEST_ID, Instant.parse("2026-07-16T00:00:00Z"))
		));
		when(service.createGuestReading(GUEST_ID, "127.0.0.1", REQUEST_ID, tarotRequest()))
			.thenThrow(new RequiredConsentMissingException("필수 동의가 필요합니다."));

		mockMvc.perform(post("/api/readings")
				.cookie(new jakarta.servlet.http.Cookie(
					CookieService.GUEST_COOKIE_NAME,
					"signed-token"
				))
				.with(request -> {
					request.setRemoteAddr("127.0.0.1");
					return request;
				})
				.contentType("application/json")
				.content(validTarotRequest()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").exists());
	}

	@Test
	void createsCompletedReadingForConsentedGuest() throws Exception {
		when(signer.verify("signed-token")).thenReturn(java.util.Optional.of(
			new GuestSession(GUEST_ID, Instant.parse("2026-07-16T00:00:00Z"))
		));
		when(service.createGuestReading(GUEST_ID, "127.0.0.1", REQUEST_ID, tarotRequest()))
			.thenReturn(new CreatedReadingResponse(
				READING_ID,
				ReadingKind.TAROT,
				"completed",
				"Tarot reading",
				tarotRequest().storageInput(),
				Map.of(
					"title", "타로 데모 리딩",
					"summary", "세 장의 카드가 현재 흐름을 보여줍니다.",
					"sections", List.of(Map.of(
						"heading", "현재의 흐름",
						"body", "질문과 선택한 카드를 바탕으로 차분히 흐름을 살펴봅니다."
					)),
					"guidance", List.of("작은 행동 하나를 먼저 정하세요."),
					"disclaimer", "이 리딩은 오락과 자기성찰을 위한 참고 자료입니다."
				),
				null,
				Instant.parse("2026-06-16T00:00:00Z"),
				Instant.parse("2026-06-16T00:00:00Z")
			));

		mockMvc.perform(post("/api/readings")
				.cookie(new jakarta.servlet.http.Cookie(
					CookieService.GUEST_COOKIE_NAME,
					"signed-token"
				))
				.with(request -> {
					request.setRemoteAddr("127.0.0.1");
					return request;
				})
				.contentType("application/json")
				.content(validTarotRequest()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reading.id").value(READING_ID.toString()))
			.andExpect(jsonPath("$.reading.kind").value("tarot"))
			.andExpect(jsonPath("$.reading.status").value("completed"))
			.andExpect(jsonPath("$.reading.result.title").value("타로 데모 리딩"))
			.andExpect(jsonPath("$.reading.result.sections[0].heading").value("현재의 흐름"));

		verify(service).createGuestReading(GUEST_ID, "127.0.0.1", REQUEST_ID, tarotRequest());
	}

	@Test
	void listsAuthenticatedUserReadings() throws Exception {
		TestingAuthenticationToken authentication = authentication();
		when(userResolver.requireUser(authentication)).thenReturn(new AuthenticatedUser(USER_ID));
		when(recordsService.listByUser(USER_ID)).thenReturn(List.of(completedReading()));

		mockMvc.perform(get("/api/readings").principal(authentication))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].id").value(READING_ID.toString()))
			.andExpect(jsonPath("$.items[0].kind").value("tarot"));
	}

	@Test
	void returnsReadingDetailForAuthenticatedUser() throws Exception {
		TestingAuthenticationToken authentication = authentication();
		when(userResolver.requireUser(authentication)).thenReturn(new AuthenticatedUser(USER_ID));
		when(recordsService.getByUserAndId(USER_ID, READING_ID)).thenReturn(completedReading());

		mockMvc.perform(get("/api/readings/{readingId}", READING_ID).principal(authentication))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reading.id").value(READING_ID.toString()))
			.andExpect(jsonPath("$.reading.status").value("completed"));
	}

	@Test
	void returnsNotFoundForMissingReadingDetail() throws Exception {
		TestingAuthenticationToken authentication = authentication();
		when(userResolver.requireUser(authentication)).thenReturn(new AuthenticatedUser(USER_ID));
		when(recordsService.getByUserAndId(USER_ID, READING_ID))
			.thenThrow(new ReadingRecordNotFoundException());

		mockMvc.perform(get("/api/readings/{readingId}", READING_ID).principal(authentication))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error").value("리딩을 찾을 수 없습니다."));
	}

	@Test
	void softDeletesAuthenticatedUserReading() throws Exception {
		TestingAuthenticationToken authentication = authentication();
		when(userResolver.requireUser(authentication)).thenReturn(new AuthenticatedUser(USER_ID));

		mockMvc.perform(delete("/api/readings/{readingId}", READING_ID).principal(authentication))
			.andExpect(status().isNoContent());

		verify(recordsService).deleteByUserAndId(USER_ID, READING_ID);
	}

	@Test
	void retriesAuthenticatedUserReading() throws Exception {
		TestingAuthenticationToken authentication = authentication();
		when(userResolver.requireUser(authentication)).thenReturn(new AuthenticatedUser(USER_ID));
		when(recordsService.retry(USER_ID, READING_ID)).thenReturn(completedReading());

		mockMvc.perform(post("/api/readings/{readingId}/retry", READING_ID).principal(authentication))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reading.id").value(READING_ID.toString()))
			.andExpect(jsonPath("$.reading.status").value("completed"));
	}

	@Test
	void returnsBadGatewayWhenOpenAiGenerationFails() throws Exception {
		when(signer.verify("signed-token")).thenReturn(java.util.Optional.of(
			new GuestSession(GUEST_ID, Instant.parse("2026-07-16T00:00:00Z"))
		));
		when(service.createGuestReading(GUEST_ID, "127.0.0.1", REQUEST_ID, tarotRequest()))
			.thenThrow(new OpenAiReadingGenerationException());

		mockMvc.perform(post("/api/readings")
				.cookie(new jakarta.servlet.http.Cookie(
					CookieService.GUEST_COOKIE_NAME,
					"signed-token"
				))
				.with(request -> {
					request.setRemoteAddr("127.0.0.1");
					return request;
				})
				.contentType("application/json")
				.content(validTarotRequest()))
			.andExpect(status().isBadGateway())
			.andExpect(jsonPath("$.error").value("OpenAI reading generation failed"))
			.andExpect(jsonPath("$.code").value("OPENAI_READING_GENERATION_FAILED"));
	}

	@Test
	void rejectsUnknownReadingRequestFields() throws Exception {
		when(signer.verify("signed-token")).thenReturn(java.util.Optional.of(
			new GuestSession(GUEST_ID, Instant.parse("2026-07-16T00:00:00Z"))
		));

		mockMvc.perform(post("/api/readings")
				.cookie(new jakarta.servlet.http.Cookie(
					CookieService.GUEST_COOKIE_NAME,
					"signed-token"
				))
				.contentType("application/json")
				.content("""
					{
					  "kind":"tarot",
					  "question":"How is today?",
					  "requestId":"82ed11d5-2269-438c-9815-42e6f13735f4",
					  "cardIds":["major-00-fool","major-01-magician","major-02-high-priestess"],
					  "unexpected": true
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").exists());

		verifyNoInteractions(service);
	}

	@Test
	void rejectsReadingRequestWithoutRequestId() throws Exception {
		when(signer.verify("signed-token")).thenReturn(java.util.Optional.of(
			new GuestSession(GUEST_ID, Instant.parse("2026-07-16T00:00:00Z"))
		));

		mockMvc.perform(post("/api/readings")
				.cookie(new jakarta.servlet.http.Cookie(
					CookieService.GUEST_COOKIE_NAME,
					"signed-token"
				))
				.contentType("application/json")
				.content("""
					{
					  "kind":"tarot",
					  "question":"How is today?",
					  "cardIds":["major-00-fool","major-01-magician","major-02-high-priestess"]
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").exists());

		verifyNoInteractions(service);
	}

	private String validTarotRequest() {
		return """
			{
			  "kind":"tarot",
			  "question":"오늘의 흐름은?",
			  "requestId":"82ed11d5-2269-438c-9815-42e6f13735f4",
			  "cardIds":["major-00-fool","major-01-magician","major-02-high-priestess"]
			}
			""";
	}

	private ReadingCreateRequest tarotRequest() {
		return new ReadingCreateRequest(
			"tarot",
			"오늘의 흐름은?",
			REQUEST_ID,
			List.of(
				"major-00-fool",
				"major-01-magician",
				"major-02-high-priestess"
			),
			null,
			null,
			null
		);
	}

	private CreatedReadingResponse completedReading() {
		return new CreatedReadingResponse(
			READING_ID,
			ReadingKind.TAROT,
			"completed",
			"Tarot reading",
			Map.of("question", "?ㅻ뒛???먮쫫??"),
			Map.of(
				"title", "?濡??곕え 由щ뵫",
				"summary", "???μ쓽 移대뱶媛 ?꾩옱 ?먮쫫??蹂댁뿬以띾땲??",
				"sections", List.of(Map.of(
					"heading", "?꾩옱???먮쫫",
					"body", "吏덈Ц怨??좏깮??移대뱶瑜?諛뷀깢?쇰줈 李⑤텇???먮쫫???댄렣遊낅땲??"
				)),
				"guidance", List.of("?묒? ?됰룞 ?섎굹瑜?癒쇱? ?뺥븯?몄슂."),
				"disclaimer", "??由щ뵫? ?ㅻ씫怨??먭린?깆같???꾪븳 李멸퀬 ?먮즺?낅땲??"
			),
			null,
			Instant.parse("2026-06-16T00:00:00Z"),
			Instant.parse("2026-06-16T00:00:01Z")
		);
	}

	private TestingAuthenticationToken authentication() {
		Jwt jwt = Jwt.withTokenValue("token")
			.header("alg", "RS256")
			.subject(USER_ID.toString())
			.build();
		return new TestingAuthenticationToken(jwt, null);
	}
}
