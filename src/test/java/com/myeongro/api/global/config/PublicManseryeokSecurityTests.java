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
	"app.frontend-origin=http://localhost:3000",
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
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
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
	private static final String ORIGIN="http://localhost:3000";
	@Test void allowedOriginPreflightIsAnsweredWithoutQuotaOrCredentials() throws Exception {
		for (String[] target:new String[][]{{"POST","/api/saju/calculations"},{"GET","/api/saju/calculations/birth-places"}}) {
			mvc.perform(options(target[1]).header("Origin",ORIGIN).header("Access-Control-Request-Method",target[0])
					.header("Access-Control-Request-Headers","content-type"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin",ORIGIN))
				.andExpect(header().string("Access-Control-Max-Age","600"))
				.andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
		}
		verifyNoInteractions(limiter,calculation,catalog,profiles,users);
	}
	@Test void actualResponsesIncludingRejectionsCarryCorsHeaders(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
		when(limiter.acquire(any(),anyString())).thenReturn(IpRateLimiter.Decision.ALLOWED);
		mvc.perform(post("/api/saju/calculations").header("Origin",ORIGIN).contentType(MediaType.APPLICATION_JSON).content("{\"birthProfile\":{}}"))
			.andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin",ORIGIN))
			.andExpect(header().string("Access-Control-Expose-Headers","Retry-After"))
			.andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
		when(limiter.acquire(any(),anyString())).thenReturn(IpRateLimiter.Decision.LIMITED);
		mvc.perform(post("/api/saju/calculations").header("Origin",ORIGIN).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isTooManyRequests()).andExpect(header().string("Access-Control-Allow-Origin",ORIGIN))
			.andExpect(header().string("Retry-After","60"));
		when(limiter.acquire(any(),anyString())).thenReturn(IpRateLimiter.Decision.UNAVAILABLE);
		mvc.perform(post("/api/saju/calculations").header("Origin",ORIGIN).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isServiceUnavailable()).andExpect(header().string("Access-Control-Allow-Origin",ORIGIN));
		when(limiter.acquire(any(),anyString())).thenReturn(IpRateLimiter.Decision.ALLOWED);
		when(calculation.calculate(any())).thenThrow(new ManseryeokCalculationService.Unavailable("SAJU_CALCULATION_BUSY"));
		mvc.perform(post("/api/saju/calculations").header("Origin",ORIGIN).contentType(MediaType.APPLICATION_JSON).content("{\"birthProfile\":{}}"))
			.andExpect(status().isServiceUnavailable()).andExpect(header().string("Access-Control-Allow-Origin",ORIGIN));
		// Rejections are logged by code only, for the CloudWatch metric filters; never the IP or the request body.
		for (String code:new String[]{"SAJU_CALCULATION_RATE_LIMITED","SAJU_CALCULATION_RATE_LIMIT_UNAVAILABLE","SAJU_CALCULATION_BUSY"}) {
			assertThat(output.getOut()).contains("SAJU_CALCULATION_REJECTED code="+code);
		}
		assertThat(output.getOut().lines().filter(line->line.contains("SAJU_CALCULATION_REJECTED")))
			.allSatisfy(line->assertThat(line).doesNotContain("127.0.0.1","birthProfile"));
	}
	@Test void otherOriginsAndOtherPathsGetNoCors() throws Exception {
		mvc.perform(options("/api/saju/calculations").header("Origin","https://evil.example").header("Access-Control-Request-Method","POST"))
			.andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
		mvc.perform(post("/api/saju/calculations").header("Origin","https://evil.example").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
		for (String path:new String[]{"/api/saju/readings","/api/auth/me","/api/saju/birth-places","/api/reading-credits/pricing"}) {
			mvc.perform(options(path).header("Origin",ORIGIN).header("Access-Control-Request-Method","POST"))
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
			mvc.perform(get(path).header("Origin",ORIGIN)).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
		}
		verifyNoInteractions(limiter,calculation);
	}
	@Test void allowedOriginsMustBeExplicit() {
		assertThat(SajuPublicCalculationCors.filter(" https://www.myeongro.com/ , http://localhost:3000")).isNotNull();
		for (String bad:new String[]{"", " , ", "*", "https://www.myeongro.com,*"}) {
			assertThatThrownBy(()->SajuPublicCalculationCors.filter(bad)).as(bad).isInstanceOf(IllegalStateException.class);
		}
	}
}
