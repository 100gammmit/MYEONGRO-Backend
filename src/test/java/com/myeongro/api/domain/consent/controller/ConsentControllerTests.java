package com.myeongro.api.domain.consent.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.myeongro.api.domain.consent.dto.ConsentStatus;
import com.myeongro.api.domain.consent.entity.ConsentDocumentType;
import com.myeongro.api.domain.consent.entity.ConsentScope;
import com.myeongro.api.domain.consent.service.ConsentService;
import com.myeongro.api.domain.consent.service.ConsentVersionMismatchException;
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
	void returnsConsentStatusForTheRequestedFeatureScope() throws Exception {
		TestingAuthenticationToken authentication = authentication();
		when(userResolver.requireUser(authentication)).thenReturn(new AuthenticatedUser(USER_ID));
		when(consentService.getUserStatus(USER_ID, ConsentScope.TAROT))
			.thenReturn(new ConsentStatus(
				List.of(ConsentDocumentType.TERMS),
				ConsentScope.TAROT.requiredDocuments(),
				false
			));

		mockMvc.perform(get("/api/consents?scope=tarot").principal(authentication))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status.hasAcceptedRequired").value(false))
			.andExpect(jsonPath("$.status.requiredDocumentTypes[1]")
				.value("ai-overseas-transfer"));

		verify(consentService).getUserStatus(USER_ID, ConsentScope.TAROT);
	}

	@Test
	void returnsOnlyTermsAndOverseasTransferForSaju() throws Exception {
		TestingAuthenticationToken authentication = authentication();
		when(userResolver.requireUser(authentication)).thenReturn(new AuthenticatedUser(USER_ID));
		when(consentService.getUserStatus(USER_ID, ConsentScope.SAJU))
			.thenReturn(new ConsentStatus(
				ConsentScope.SAJU.requiredDocuments(),
				ConsentScope.SAJU.requiredDocuments(),
				true
			));

		mockMvc.perform(get("/api/consents?scope=saju").principal(authentication))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status.hasAcceptedRequired").value(true))
			.andExpect(jsonPath("$.status.requiredDocumentTypes.length()").value(2))
			.andExpect(jsonPath("$.status.requiredDocumentTypes[0]").value("terms"))
			.andExpect(jsonPath("$.status.requiredDocumentTypes[1]")
				.value("ai-overseas-transfer"));

		verify(consentService).getUserStatus(USER_ID, ConsentScope.SAJU);
	}

	@Test
	void recordsAllRequiredDocumentsInOneRequest() throws Exception {
		TestingAuthenticationToken authentication = authentication();
		when(userResolver.requireUser(authentication)).thenReturn(new AuthenticatedUser(USER_ID));
		when(consentService.completeRequiredForUser(
			USER_ID,
			ConsentScope.TAROT,
			Map.of(
				"terms", "2026-08-28",
				"ai-overseas-transfer", "draft-2026-09-07"
			)
		)).thenReturn(new ConsentStatus(
			ConsentScope.TAROT.requiredDocuments(),
			ConsentScope.TAROT.requiredDocuments(),
			true
		));

		mockMvc.perform(post("/api/consents")
				.principal(authentication)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "scope":"tarot",
					  "documentVersions":{
					    "terms":"2026-08-28",
					    "ai-overseas-transfer":"draft-2026-09-07"
					  }
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status.hasAcceptedRequired").value(true));
	}

	@Test
	void recordsSajuConsentWithTheSharedTwoDocumentContract() throws Exception {
		TestingAuthenticationToken authentication = authentication();
		when(userResolver.requireUser(authentication)).thenReturn(new AuthenticatedUser(USER_ID));
		Map<String, String> versions = Map.of(
			"terms", "2026-08-28",
			"ai-overseas-transfer", "draft-2026-09-07"
		);
		when(consentService.completeRequiredForUser(
			USER_ID,
			ConsentScope.SAJU,
			versions
		)).thenReturn(new ConsentStatus(
			ConsentScope.SAJU.requiredDocuments(),
			ConsentScope.SAJU.requiredDocuments(),
			true
		));

		mockMvc.perform(post("/api/consents")
				.principal(authentication)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "scope":"saju",
					  "documentVersions":{
					    "terms":"2026-08-28",
					    "ai-overseas-transfer":"draft-2026-09-07"
					  }
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status.hasAcceptedRequired").value(true));

		verify(consentService).completeRequiredForUser(
			USER_ID,
			ConsentScope.SAJU,
			versions
		);
	}

	@Test
	void rejectsTheRetiredSajuInputDocumentInASajuRequest() throws Exception {
		TestingAuthenticationToken authentication = authentication();
		when(userResolver.requireUser(authentication)).thenReturn(new AuthenticatedUser(USER_ID));
		Map<String, String> versions = Map.of(
			"terms", "2026-08-28",
			"ai-overseas-transfer", "draft-2026-09-07",
			"saju-input", "retired-version"
		);
		when(consentService.completeRequiredForUser(
			USER_ID,
			ConsentScope.SAJU,
			versions
		)).thenThrow(new IllegalArgumentException("Unknown consent document type: saju-input"));

		mockMvc.perform(post("/api/consents")
				.principal(authentication)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "scope":"saju",
					  "documentVersions":{
					    "terms":"2026-08-28",
					    "ai-overseas-transfer":"draft-2026-09-07",
					    "saju-input":"retired-version"
					  }
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("Unknown consent document type: saju-input"));
	}

	@Test
	void returnsConflictWhenTheReviewedVersionIsNoLongerCurrent() throws Exception {
		TestingAuthenticationToken authentication = authentication();
		when(userResolver.requireUser(authentication)).thenReturn(new AuthenticatedUser(USER_ID));
		when(consentService.completeRequiredForUser(
			USER_ID,
			ConsentScope.TAROT,
			Map.of(
				"terms", "2026-08-28",
				"ai-overseas-transfer", "outdated"
			)
		)).thenThrow(new ConsentVersionMismatchException());

		mockMvc.perform(post("/api/consents")
				.principal(authentication)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "scope":"tarot",
					  "documentVersions":{
					    "terms":"2026-08-28",
					    "ai-overseas-transfer":"outdated"
					  }
					}
					"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONSENT_VERSION_MISMATCH"));
	}

	@Test
	void withdrawsFeatureConsentIndependently() throws Exception {
		TestingAuthenticationToken authentication = authentication();
		when(userResolver.requireUser(authentication)).thenReturn(new AuthenticatedUser(USER_ID));

		mockMvc.perform(delete("/api/consents/ai-overseas-transfer")
				.principal(authentication))
			.andExpect(status().isNoContent());

		verify(consentService).withdrawForUser(
			USER_ID,
			ConsentDocumentType.AI_OVERSEAS_TRANSFER
		);
	}

	@Test
	void rejectsUnknownAcceptanceFields() throws Exception {
		mockMvc.perform(post("/api/consents")
				.principal(authentication())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"scope":"tarot","documentVersions":{"terms":"2026-08-28"},"acceptedAt":"2026-09-07T00:00:00Z"}
					"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsAMissingDocumentVersion() throws Exception {
		mockMvc.perform(post("/api/consents")
				.principal(authentication())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"scope\":\"tarot\"}"))
			.andExpect(status().isBadRequest());

		org.mockito.Mockito.verifyNoInteractions(consentService);
	}

	private TestingAuthenticationToken authentication() {
		return new TestingAuthenticationToken(
			new SessionAuthenticatedPrincipal(USER_ID, "User", "kakao", "provider-user"),
			null,
			"ROLE_USER"
		);
	}
}
