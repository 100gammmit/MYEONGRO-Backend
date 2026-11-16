package com.myeongro.api.domain.saju.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.session.SessionAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import com.myeongro.api.domain.saju.calculation.*;
import com.myeongro.api.domain.saju.place.SajuBirthPlaceCatalog;
import com.myeongro.api.domain.saju.service.ManseryeokCalculationService;
import com.myeongro.api.domain.profile.repository.ProfileJpaRepository;
import com.myeongro.api.domain.eligibility.service.AdultEligibilityService;
import com.myeongro.api.global.config.SecurityConfig;
import com.myeongro.api.global.auth.*;
import com.myeongro.api.global.auth.oauth.*;
import com.myeongro.api.global.ratelimit.IpRateLimiter;

@SpringBootTest(classes=ManseryeokHttpContext.Application.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
	"spring.ai.openai.api-key=test-only-not-a-real-credential", "spring.main.banner-mode=off"
})
@org.springframework.test.context.ActiveProfiles("manseryeok-http-smoke")
abstract class ManseryeokHttpContext {
	@Configuration(proxyBeanMethods=false)
	@Profile("manseryeok-http-smoke")
	@EnableAutoConfiguration(exclude={DataSourceAutoConfiguration.class,HibernateJpaAutoConfiguration.class,FlywayAutoConfiguration.class,RedisAutoConfiguration.class,RedisRepositoriesAutoConfiguration.class,SessionAutoConfiguration.class})
	@Import({SecurityConfig.class,ActiveAccountSessionFilter.class,AdultEligibilitySessionFilter.class,PendingSignupAccessFilter.class,
		SajuCalculationController.class,SajuCalculationExceptionHandler.class,ManseryeokCalculationService.class,SajuCalculationService.class,
		SajuBirthPlaceCatalog.class,TrueSolarTimeCorrector.class,ApproximateBirthTimeResolver.class,LunarJavaFourPillarsAdapter.class,ManseryeokChartBuilder.class,SajuMonthlyCalendarService.class})
	static class Application { }
	@LocalServerPort int port;
	@MockitoBean IpRateLimiter limiter;
	@MockitoBean ProfileJpaRepository profiles;
	@MockitoBean AdultEligibilityService eligibility;
	@MockitoBean SignupAttemptCoordinator signup;
	@MockitoBean OAuth2SessionUserService oauth;
	@MockitoBean OidcSessionUserService oidc;
	HttpResponse<String> request(String method,String path,String body) throws Exception {
		var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Content-Type","application/json")
			.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build();
		return HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());
	}
}

@TestPropertySource(properties="app.saju.public-calculation.enabled=true")
class ManseryeokHttpSmokeTests extends ManseryeokHttpContext {
	@Test void actualHttpAndOpenApiUseRealCalculationWithoutDatabaseRedisOrAi() throws Exception {
		when(limiter.acquire(any(),anyString())).thenReturn(IpRateLimiter.Decision.ALLOWED);
		String body="""
			{"birthProfile":{"calendarType":"solar","birthDate":"2000-06-15","birthTime":"14:30","birthTimePrecision":"exact","provinceCode":"11","luckDirectionBasis":"male"}}
			""";
		var response=request("POST","/api/saju/calculations",body);
		assertThat(response.statusCode()).isEqualTo(200); assertThat(response.body()).contains("manseryeok-ko-v1","calculationFingerprint","갑진");
		assertThat(response.headers().firstValue("cache-control")).contains("no-store");
		assertThat(response.headers().allValues("set-cookie")).isEmpty();
		clearInvocations(limiter);
		assertThat(request("POST","/api/saju/%63alculations",body).statusCode()).isEqualTo(200);
		verify(limiter).acquire(eq(IpRateLimiter.Scope.SAJU_CALCULATION),anyString());
		when(limiter.acquire(any(),anyString())).thenReturn(IpRateLimiter.Decision.LIMITED);
		assertThat(request("POST","/api/saju/%63alculations",body).statusCode()).isEqualTo(429);
		when(limiter.acquire(any(),anyString())).thenReturn(IpRateLimiter.Decision.UNAVAILABLE);
		assertThat(request("POST","/api/saju/%63alculations",body).statusCode()).isEqualTo(503);
		when(limiter.acquire(any(),anyString())).thenReturn(IpRateLimiter.Decision.ALLOWED);
		var places=request("GET","/api/saju/calculations/birth-places",null);
		assertThat(places.statusCode()).isEqualTo(200); assertThat(places.body()).contains("cityCode").doesNotContain("longitude","latitude");
		var openapi=request("GET","/v3/api-docs",null);
		assertThat(openapi.statusCode()).isEqualTo(200); assertThat(openapi.body()).contains("/api/saju/calculations","SajuCalculationRequest","ManseryeokChart","MIDNIGHT");
		var invalid=request("POST","/api/saju/calculations","{\"birthProfile\":{}}");
		assertThat(invalid.statusCode()).isEqualTo(400); assertThat(invalid.body()).contains("code","field");
		assertThat(request("POST","/api/saju/readings",body).statusCode()).isEqualTo(401);
		verifyNoInteractions(profiles,eligibility,signup);
	}
}

class ManseryeokDisabledHttpSmokeTests extends ManseryeokHttpContext {
	@Test void defaultOffStopsHttpBeforeMalformedBodyAndRateLimit() throws Exception {
		assertThat(request("POST","/api/saju/calculations","{invalid").statusCode()).isEqualTo(404);
		assertThat(request("GET","/api/saju/calculations/birth-places",null).statusCode()).isEqualTo(404);
		assertThat(request("POST","/api/saju/%63alculations","{invalid").statusCode()).isEqualTo(404);
		assertThat(request("GET","/api/saju/calculations/%62irth-places",null).statusCode()).isEqualTo(404);
		verifyNoInteractions(limiter,profiles,eligibility,signup);
	}
}
