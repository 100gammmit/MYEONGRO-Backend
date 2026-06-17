package com.myeongro.api.domain.reading.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.reading.dto.CreatedReadingResponse;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.service.OpenAiReadingGenerationException;
import com.myeongro.api.domain.reading.service.ReadingCreationService;
import com.myeongro.api.domain.reading.service.RequiredConsentMissingException;
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

	private ReadingCreationService service;
	private GuestSessionSigner signer;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		service = mock(ReadingCreationService.class);
		signer = mock(GuestSessionSigner.class);
		mockMvc = MockMvcBuilders
			.standaloneSetup(new ReadingController(service, signer))
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
}
