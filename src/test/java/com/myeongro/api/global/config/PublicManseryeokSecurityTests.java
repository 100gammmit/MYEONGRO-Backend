package com.myeongro.api.global.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.*;
import com.myeongro.api.domain.saju.controller.*;
import com.myeongro.api.domain.saju.service.ManseryeokCalculationService;
import com.myeongro.api.domain.saju.place.SajuBirthPlaceCatalog;
import com.myeongro.api.domain.profile.repository.ProfileJpaRepository;
import com.myeongro.api.domain.eligibility.service.AdultEligibilityService;
import com.myeongro.api.global.auth.*;
import com.myeongro.api.global.auth.oauth.*;
import com.myeongro.api.global.ratelimit.IpRateLimiter;

@WebMvcTest(controllers=SajuCalculationController.class)
@Import({SecurityConfig.class,ActiveAccountSessionFilter.class,AdultEligibilitySessionFilter.class,PendingSignupAccessFilter.class,SajuCalculationExceptionHandler.class})
@TestPropertySource(properties={
	"app.frontend-origin=http://localhost:3000", "app.saju.public-calculation.enabled=true",
	"app.eligibility.adult-policy-version=2026-09-09",
	"spring.security.oauth2.client.registration.kakao.client-id=test-client-id",
	"spring.security.oauth2.client.registration.kakao.client-secret=test-client-secret",
	"spring.security.oauth2.client.registration.kakao.redirect-uri=http://localhost/login/oauth2/code/kakao",
	"spring.security.oauth2.client.registration.kakao.authorization-grant-type=authorization_code",
	"spring.security.oauth2.client.provider.kakao.authorization-uri=https://kauth.kakao.com/oauth/authorize",
	"spring.security.oauth2.client.provider.kakao.token-uri=https://kauth.kakao.com/oauth/token",
	"spring.security.oauth2.client.provider.kakao.user-info-uri=https://kapi.kakao.com/v2/user/me",
	"spring.security.oauth2.client.provider.kakao.user-name-attribute=id"
})
class PublicManseryeokSecurityTests {
	@Autowired MockMvc mvc;
	@MockitoBean IpRateLimiter limiter;
	@MockitoBean ManseryeokCalculationService calculation;
	@MockitoBean com.myeongro.api.domain.readingcredit.service.ReadingCreditService credits;
	@MockitoBean SajuBirthPlaceCatalog catalog;
	@MockitoBean ProfileJpaRepository profiles;
	@MockitoBean AdultEligibilityService eligibility;
	@MockitoBean AuthenticatedUserResolver users;
	@MockitoBean SignupAttemptCoordinator signup;
	@MockitoBean OAuth2SessionUserService oauth;
	@MockitoBean OidcSessionUserService oidc;
	@Test void memberPendingSignupAndUnconfirmedAdultAllUsePublicCalculationWithoutAccountLookups() throws Exception {
		when(limiter.acquire(any(),anyString())).thenReturn(IpRateLimiter.Decision.ALLOWED);
		var pending=new PendingOAuth2User(new OAuthProviderUserInfo("google","synthetic"),"test-only",java.util.Map.of("sub","synthetic"),java.util.List.of());
		var member=new com.myeongro.api.global.auth.session.SessionAuthenticatedPrincipal(java.util.UUID.fromString("43bc72f9-eed1-4e4b-8717-6fe969b4ea43"));
		for(Object principal:new Object[]{pending,member}) {
			var authentication=new org.springframework.security.authentication.TestingAuthenticationToken(principal,null,"ROLE_USER");
			mvc.perform(post("/api/saju/calculations").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(authentication))
				.contentType(MediaType.APPLICATION_JSON).content("{\"birthProfile\":{}}"))
				.andExpect(status().isOk());
		}
		verifyNoInteractions(profiles,eligibility,users,signup,credits);
	}
	@Test void guestCanCalculateWithoutSessionAndCatalogDoesNotUseQuota() throws Exception {
		when(limiter.acquire(eq(IpRateLimiter.Scope.SAJU_CALCULATION),anyString())).thenReturn(IpRateLimiter.Decision.ALLOWED);
		var result=mvc.perform(post("/api/saju/calculations").contentType(MediaType.APPLICATION_JSON).content("{\"birthProfile\":{}}"))
			.andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn();
		assertThat(result.getRequest().getSession(false)).isNull();
		verify(calculation).calculate(any());
		clearInvocations(limiter);
		when(catalog.publicVersion()).thenReturn("kr-admin-v1");
		when(catalog.publicProvinces()).thenReturn(java.util.List.of());
		mvc.perform(get("/api/saju/calculations/birth-places")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
		verifyNoInteractions(limiter,profiles,eligibility,users,signup);
	}
	@Test void quotaAndRedisFailureStopBeforeParsingAndBodySizeIsStillLimited() throws Exception {
		when(limiter.acquire(any(),anyString())).thenReturn(IpRateLimiter.Decision.LIMITED);
		mvc.perform(post("/api/saju/calculations").contentType(MediaType.APPLICATION_JSON).content("not json"))
			.andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","60"));
		when(limiter.acquire(any(),anyString())).thenReturn(IpRateLimiter.Decision.UNAVAILABLE);
		mvc.perform(post("/api/saju/calculations").contentType(MediaType.APPLICATION_JSON).content("not json")).andExpect(status().isServiceUnavailable());
		when(limiter.acquire(any(),anyString())).thenReturn(IpRateLimiter.Decision.ALLOWED);
		mvc.perform(post("/api/saju/calculations").contentType(MediaType.APPLICATION_JSON).content("x".repeat(32769))).andExpect(status().isPayloadTooLarge());
		verifyNoInteractions(calculation,profiles,eligibility,users);
	}
	@Test void malformedBodyIsSanitizedAndQueriesAreNotAccepted() throws Exception {
		when(limiter.acquire(any(),anyString())).thenReturn(IpRateLimiter.Decision.ALLOWED);
		mvc.perform(post("/api/saju/calculations").contentType(MediaType.APPLICATION_JSON).content("{private-birth-input"))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CALCULATION_REQUEST"))
			.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-birth-input"))));
		clearInvocations(limiter);
		mvc.perform(post("/api/saju/calculations?birthDate=private")).andExpect(status().isBadRequest());
		verifyNoInteractions(limiter,calculation);
	}
	@Test void otherMethodsAndProtectedReadingPathsRemainProtected() throws Exception {
		mvc.perform(get("/api/saju/calculations")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/saju/calculations/birth-places")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/saju/birth-places")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/saju/readings")).andExpect(status().isUnauthorized());
		verifyNoInteractions(calculation,catalog,limiter);
	}
	@Test void disabledFilterReturns404WithoutReadingBodyOrCountingRequests() throws Exception {
		var filter=new SajuPublicCalculationFilter(false,limiter);
		for (String path:new String[]{"/api/saju/calculations","/api/saju/calculations/birth-places"}) {
			var request=new MockHttpServletRequest(path.endsWith("birth-places") ? "GET":"POST",path) {
				@Override public jakarta.servlet.ServletInputStream getInputStream() { throw new AssertionError("disabled gate read body"); }
			};
			var response=new MockHttpServletResponse();
			filter.doFilter(request,response,(req,res)->{throw new AssertionError("disabled gate passed");});
			assertThat(response.getStatus()).isEqualTo(404); assertThat(request.getSession(false)).isNull();
		}
		verifyNoInteractions(limiter,calculation);
	}
}
