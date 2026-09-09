package com.myeongro.api.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;

import com.myeongro.api.global.auth.ActiveAccountSessionFilter;
import com.myeongro.api.global.auth.AdultEligibilitySessionFilter;
import com.myeongro.api.domain.eligibility.service.AdultEligibilityService;
import com.myeongro.api.global.auth.oauth.OAuth2SessionUserService;
import com.myeongro.api.global.auth.oauth.OAuth2LoginSuccessHandler;
import com.myeongro.api.global.auth.oauth.OAuth2NextRequestFilter;
import com.myeongro.api.global.auth.oauth.OidcSessionUserService;

/**
 * SecurityConfig
 * <p></p>
 * @author 100minha
 */
@Configuration
public class SecurityConfig {

	private final String frontendOrigin;
	private final OAuth2SessionUserService oauth2SessionUserService;
	private final OidcSessionUserService oidcSessionUserService;
	private final ActiveAccountSessionFilter activeAccountSessionFilter;
	private final AdultEligibilitySessionFilter adultEligibilitySessionFilter;
	private final AdultEligibilityService adultEligibilityService;
	private final String adultPolicyVersion;

	public SecurityConfig(
		@Value("${app.frontend-origin:http://localhost:3000}") String frontendOrigin,
		OAuth2SessionUserService oauth2SessionUserService,
		OidcSessionUserService oidcSessionUserService,
		ActiveAccountSessionFilter activeAccountSessionFilter,
		AdultEligibilitySessionFilter adultEligibilitySessionFilter,
		AdultEligibilityService adultEligibilityService,
		@Value("${app.eligibility.adult-policy-version}") String adultPolicyVersion
	) {
		this.frontendOrigin = frontendOrigin;
		this.oauth2SessionUserService = oauth2SessionUserService;
		this.oidcSessionUserService = oidcSessionUserService;
		this.activeAccountSessionFilter = activeAccountSessionFilter;
		this.adultEligibilitySessionFilter = adultEligibilitySessionFilter;
		this.adultEligibilityService = adultEligibilityService;
		this.adultPolicyVersion = adultPolicyVersion;
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
			.csrf(csrf -> csrf.disable())
			.sessionManagement(session -> session
				.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
			)
			.addFilterBefore(
				new OAuth2NextRequestFilter(adultPolicyVersion),
				OAuth2AuthorizationRequestRedirectFilter.class
			)
			.addFilterAfter(
				activeAccountSessionFilter,
				SecurityContextHolderFilter.class
			)
			.addFilterAfter(
				adultEligibilitySessionFilter,
				ActiveAccountSessionFilter.class
			)
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(
					"/actuator/health",
					"/actuator/health/**",
					"/v3/api-docs/**",
					"/swagger-ui/**",
					"/swagger-ui.html",
					"/oauth2/authorization/**",
					"/login/oauth2/code/**"
				).permitAll()
				.requestMatchers(HttpMethod.GET, "/api/auth/me").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/auth/logout").permitAll()
				.requestMatchers(
					HttpMethod.POST, "/api/tarot/daily-card-selections"
				).permitAll()
				.anyRequest().authenticated()
			)
			.oauth2Login(oauth2 -> oauth2
				.userInfoEndpoint(userInfo -> userInfo
					.userService(oauth2SessionUserService)
					.oidcUserService(oidcSessionUserService)
				)
				.successHandler(new OAuth2LoginSuccessHandler(
					frontendOrigin,
					adultEligibilityService
				))
			)
			.exceptionHandling(exceptions -> exceptions
				.defaultAuthenticationEntryPointFor(
					new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
					new RegexRequestMatcher("^/api/.*", null)
				)
			)
			.build();
	}
}
