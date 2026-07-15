package com.myeongro.api.domain.consent.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.myeongro.api.domain.consent.dto.ConsentAcceptance;
import com.myeongro.api.domain.consent.dto.ConsentStatus;
import com.myeongro.api.domain.consent.entity.ConsentDocumentType;
import com.myeongro.api.domain.consent.service.ConsentService;
import com.myeongro.api.global.auth.AuthenticatedUser;
import com.myeongro.api.global.auth.AuthenticatedUserResolver;
import com.myeongro.api.global.auth.session.SessionAuthenticatedPrincipal;

class ConsentControllerTests {

	private static final UUID USER_ID = UUID.fromString(
		"3b413be2-2b81-4802-8c6a-f868a85d8d83"
	);

	private ConsentService consentService;
	private AuthenticatedUserResolver userResolver;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		consentService = org.mockito.Mockito.mock(ConsentService.class);
		userResolver = org.mockito.Mockito.mock(AuthenticatedUserResolver.class);
		mockMvc = MockMvcBuilders.standaloneSetup(
			new ConsentController(consentService, userResolver)
		).build();
	}

	@Test
	void returnsAuthenticatedUsersConsentStatus() throws Exception {
		TestingAuthenticationToken authentication = authentication();
		when(userResolver.requireUser(authentication)).thenReturn(new AuthenticatedUser(USER_ID));
		when(consentService.getUserStatus(USER_ID)).thenReturn(new ConsentStatus(
			ConsentDocumentType.required(),
			ConsentDocumentType.required(),
			true
		));

		mockMvc.perform(get("/api/consents").principal(authentication))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status.hasAcceptedRequired").value(true));

		verify(consentService).getUserStatus(USER_ID);
	}

	@Test
	void recordsRequiredConsentForAuthenticatedUser() throws Exception {
		TestingAuthenticationToken authentication = authentication();
		when(userResolver.requireUser(authentication)).thenReturn(new AuthenticatedUser(USER_ID));
		when(consentService.acceptRequiredForUser(USER_ID, ConsentDocumentType.required()))
			.thenReturn(List.of(new ConsentAcceptance(
				ConsentDocumentType.TERMS,
				"2026-06-10",
				Instant.parse("2026-06-15T00:00:00Z")
			)));

		mockMvc.perform(post("/api/consents")
				.principal(authentication)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"acceptedDocumentTypes":["terms","privacy","sensitive-data"]}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.consents[0].documentType").value("terms"));

		verify(consentService).acceptRequiredForUser(USER_ID, ConsentDocumentType.required());
	}

	@Test
	void rejectsUnknownConsentField() throws Exception {
		mockMvc.perform(post("/api/consents")
				.principal(authentication())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "acceptedDocumentTypes":["terms","privacy","sensitive-data"],
					  "acceptedAt":"2026-06-15T00:00:00Z"
					}
					"""))
			.andExpect(status().isBadRequest());
	}

	private TestingAuthenticationToken authentication() {
		return new TestingAuthenticationToken(
			new SessionAuthenticatedPrincipal(USER_ID, "User", "kakao", "provider-user"),
			null,
			"ROLE_USER"
		);
	}
}
