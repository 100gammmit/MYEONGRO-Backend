package com.myeongro.api.domain.consent.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.consent.dto.ConsentAcceptance;
import com.myeongro.api.domain.consent.dto.ConsentStatus;
import com.myeongro.api.domain.consent.entity.ConsentDocumentType;
import com.myeongro.api.domain.consent.service.ConsentService;
import com.myeongro.api.global.auth.AuthenticatedUser;
import com.myeongro.api.global.auth.AuthenticatedUserResolver;
import com.myeongro.api.global.auth.session.SessionAuthenticatedPrincipal;
import com.myeongro.api.global.cookie.CookieService;
import com.myeongro.api.global.guest.GuestService;
import com.myeongro.api.global.guest.GuestSession;
import com.myeongro.api.global.guest.GuestSessionSigner;
import com.myeongro.api.global.guest.IssuedGuestSession;

class ConsentControllerTests {

	private static final UUID GUEST_ID =
		UUID.fromString("9775ff70-5708-45d8-85f8-cb57878bc25d");
	private static final UUID USER_ID =
		UUID.fromString("3b413be2-2b81-4802-8c6a-f868a85d8d83");
	private static final GuestSession SESSION = new GuestSession(
		GUEST_ID,
		Instant.parse("2026-07-15T00:00:00Z")
	);

	private ConsentService consentService;
	private GuestService guestService;
	private GuestSessionSigner signer;
	private AuthenticatedUserResolver userResolver;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		consentService = mock(ConsentService.class);
		guestService = mock(GuestService.class);
		signer = mock(GuestSessionSigner.class);
		userResolver = mock(AuthenticatedUserResolver.class);
		mockMvc = MockMvcBuilders
			.standaloneSetup(new ConsentController(
				consentService,
				guestService,
				signer,
				userResolver
			))
			.setMessageConverters(new MappingJackson2HttpMessageConverter(
				new ObjectMapper().findAndRegisterModules()
			))
			.build();
	}

	@Test
	void getIssuesGuestCookieAndReturnsConsentStatusWhenCookieIsMissing() throws Exception {
		ResponseCookie cookie = ResponseCookie.from(
			CookieService.GUEST_COOKIE_NAME,
			"signed-token"
		).httpOnly(true).path("/").build();
		when(guestService.issueGuest())
			.thenReturn(new IssuedGuestSession(SESSION, "signed-token", cookie));
		when(consentService.getStatus(GUEST_ID)).thenReturn(new ConsentStatus(
			List.of(),
			ConsentDocumentType.required(),
			false
		));

		mockMvc.perform(get("/api/consents"))
			.andExpect(status().isOk())
			.andExpect(cookie().value(CookieService.GUEST_COOKIE_NAME, "signed-token"))
			.andExpect(jsonPath("$.status.acceptedDocumentTypes").isEmpty())
			.andExpect(jsonPath("$.status.requiredDocumentTypes[0]").value("terms"))
			.andExpect(jsonPath("$.status.hasAcceptedRequired").value(false));
	}

	@Test
	void getReusesValidGuestCookieWithoutIssuingAnotherCookie() throws Exception {
		when(signer.verify("signed-token")).thenReturn(Optional.of(SESSION));
		when(consentService.getStatus(GUEST_ID)).thenReturn(new ConsentStatus(
			ConsentDocumentType.required(),
			ConsentDocumentType.required(),
			true
		));

		mockMvc.perform(get("/api/consents")
				.cookie(new jakarta.servlet.http.Cookie(
					CookieService.GUEST_COOKIE_NAME,
					"signed-token"
				)))
			.andExpect(status().isOk())
			.andExpect(cookie().doesNotExist(CookieService.GUEST_COOKIE_NAME))
			.andExpect(jsonPath("$.status.hasAcceptedRequired").value(true));

		verify(guestService, never()).issueGuest();
	}

	@Test
	void getReturnsAuthenticatedUserConsentStatusWithoutIssuingGuestCookie() throws Exception {
		TestingAuthenticationToken authentication = authentication();
		when(userResolver.requireUser(authentication)).thenReturn(new AuthenticatedUser(USER_ID));
		when(consentService.getUserStatus(USER_ID)).thenReturn(new ConsentStatus(
			ConsentDocumentType.required(),
			ConsentDocumentType.required(),
			true
		));

		mockMvc.perform(get("/api/consents").principal(authentication))
			.andExpect(status().isOk())
			.andExpect(cookie().doesNotExist(CookieService.GUEST_COOKIE_NAME))
			.andExpect(jsonPath("$.status.hasAcceptedRequired").value(true));

		verify(guestService, never()).issueGuest();
	}

	@Test
	void postRecordsConsentForVerifiedCookie() throws Exception {
		when(signer.verify("signed-token")).thenReturn(Optional.of(SESSION));
		when(consentService.acceptRequired(GUEST_ID, ConsentDocumentType.required()))
			.thenReturn(List.of(new ConsentAcceptance(
				GUEST_ID,
				ConsentDocumentType.TERMS,
				"2026-06-10",
				Instant.parse("2026-06-15T00:00:00Z")
			)));

		mockMvc.perform(post("/api/consents")
				.cookie(new jakarta.servlet.http.Cookie(
					CookieService.GUEST_COOKIE_NAME,
					"signed-token"
				))
				.contentType("application/json")
				.content("""
					{"acceptedDocumentTypes":["terms","privacy","sensitive-data"]}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.consents[0].documentType").value("terms"));
	}

	@Test
	void postRecordsConsentForAuthenticatedUser() throws Exception {
		TestingAuthenticationToken authentication = authentication();
		when(userResolver.requireUser(authentication)).thenReturn(new AuthenticatedUser(USER_ID));
		when(consentService.acceptRequiredForUser(USER_ID, ConsentDocumentType.required()))
			.thenReturn(List.of(new ConsentAcceptance(
				null,
				ConsentDocumentType.TERMS,
				"2026-06-10",
				Instant.parse("2026-06-15T00:00:00Z")
			)));

		mockMvc.perform(post("/api/consents")
				.principal(authentication)
				.contentType("application/json")
				.content("""
					{"acceptedDocumentTypes":["terms","privacy","sensitive-data"]}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.consents[0].documentType").value("terms"));

		verify(consentService).acceptRequiredForUser(USER_ID, ConsentDocumentType.required());
	}

	@Test
	void postRejectsMissingGuestCookieAndUnknownClientOwnedFields() throws Exception {
		mockMvc.perform(post("/api/consents")
				.contentType("application/json")
				.content("""
					{"acceptedDocumentTypes":["terms","privacy","sensitive-data"]}
					"""))
			.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/consents")
				.cookie(new jakarta.servlet.http.Cookie(
					CookieService.GUEST_COOKIE_NAME,
					"signed-token"
				))
				.contentType("application/json")
				.content("""
					{
					  "acceptedDocumentTypes":["terms","privacy","sensitive-data"],
					  "acceptedAt":"2026-06-15T00:00:00Z"
					}
					"""))
			.andExpect(status().isBadRequest());

		verify(consentService, never()).acceptRequired(
			GUEST_ID,
			ConsentDocumentType.required()
		);
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
