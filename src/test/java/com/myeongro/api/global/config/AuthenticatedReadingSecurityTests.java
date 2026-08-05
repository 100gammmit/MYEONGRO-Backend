package com.myeongro.api.global.config;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.myeongro.api.domain.consent.controller.ConsentController;
import com.myeongro.api.domain.consent.service.ConsentService;
import com.myeongro.api.domain.reading.controller.ReadingController;
import com.myeongro.api.domain.reading.service.ReadingCreationService;
import com.myeongro.api.domain.reading.service.ReadingRecordsService;
import com.myeongro.api.domain.saju.controller.SajuBirthPlaceController;
import com.myeongro.api.domain.saju.place.SajuBirthPlaceCatalog;
import com.myeongro.api.global.auth.AuthenticatedUserResolver;
import com.myeongro.api.global.auth.oauth.OAuth2SessionUserService;
import com.myeongro.api.global.auth.oauth.OidcSessionUserService;

@WebMvcTest(controllers = {
	ConsentController.class,
	ReadingController.class,
	SajuBirthPlaceController.class
})
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
	"app.frontend-origin=http://localhost:3000",
	"spring.security.oauth2.client.registration.kakao.client-id=test-client-id",
	"spring.security.oauth2.client.registration.kakao.client-secret=test-client-secret",
	"spring.security.oauth2.client.registration.kakao.redirect-uri=http://localhost/login/oauth2/code/kakao",
	"spring.security.oauth2.client.registration.kakao.authorization-grant-type=authorization_code",
	"spring.security.oauth2.client.registration.kakao.client-authentication-method=client_secret_post",
	"spring.security.oauth2.client.provider.kakao.authorization-uri=https://kauth.kakao.com/oauth/authorize",
	"spring.security.oauth2.client.provider.kakao.token-uri=https://kauth.kakao.com/oauth/token",
	"spring.security.oauth2.client.provider.kakao.user-info-uri=https://kapi.kakao.com/v2/user/me",
	"spring.security.oauth2.client.provider.kakao.user-name-attribute=id"
})
class AuthenticatedReadingSecurityTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ConsentService consentService;

	@MockitoBean
	private ReadingCreationService readingCreationService;

	@MockitoBean
	private ReadingRecordsService readingRecordsService;

	@MockitoBean
	private SajuBirthPlaceCatalog sajuBirthPlaceCatalog;

	@MockitoBean
	private AuthenticatedUserResolver userResolver;

	@MockitoBean
	private OAuth2SessionUserService oauth2SessionUserService;

	@MockitoBean
	private OidcSessionUserService oidcSessionUserService;

	@Test
	void unauthenticatedConsentRequestsStopBeforeService() throws Exception {
		mockMvc.perform(get("/api/consents"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/consents")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"acceptedDocumentTypes":["terms","privacy","sensitive-data"]}
					"""))
			.andExpect(status().isUnauthorized());

		verifyNoInteractions(consentService, userResolver);
	}

	@Test
	void unauthenticatedReadingCreationStopsBeforeDbOrProviderService() throws Exception {
		mockMvc.perform(post("/api/readings")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "kind":"tarot",
					  "spreadType":"daily_one_card",
					  "question":"오늘의 마음은?",
					  "requestId":"82ed11d5-2269-438c-9815-42e6f13735f4",
					  "cardIds":["major-00-fool"]
					}
					"""))
			.andExpect(status().isUnauthorized());

		verifyNoInteractions(readingCreationService, userResolver);
	}

	@Test
	void unauthenticatedBirthPlaceCatalogStopsBeforeService() throws Exception {
		mockMvc.perform(get("/api/saju/birth-places"))
			.andExpect(status().isUnauthorized());

		verifyNoInteractions(sajuBirthPlaceCatalog, consentService, userResolver);
	}
}
