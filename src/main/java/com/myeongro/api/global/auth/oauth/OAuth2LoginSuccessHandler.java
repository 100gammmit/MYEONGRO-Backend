package com.myeongro.api.global.auth.oauth;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import com.myeongro.api.domain.eligibility.service.AdultEligibilityService;
import com.myeongro.api.global.auth.AdultEligibilitySessionFilter;
import com.myeongro.api.global.auth.session.SessionPrincipal;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

	private final String frontendOrigin;
	private final AdultEligibilityService adultEligibilityService;

	public OAuth2LoginSuccessHandler(
		String frontendOrigin,
		AdultEligibilityService adultEligibilityService
	) {
		this.frontendOrigin = trimTrailingSlash(frontendOrigin);
		this.adultEligibilityService = adultEligibilityService;
	}

	@Override
	public void onAuthenticationSuccess(
		HttpServletRequest request,
		HttpServletResponse response,
		Authentication authentication
	) throws IOException, ServletException {
		HttpSession session = request.getSession(false);
		String next = readNext(session);
		if (session == null) {
			denyLogin(response, session, next);
			return;
		}

		if (authentication.getPrincipal() instanceof PendingSignupPrincipal pendingSignup) {
			storeMinimalPendingSignupPrincipal(session, authentication, pendingSignup);
			response.sendRedirect(frontendOrigin + "/signup/age");
			return;
		}

		if (!(authentication.getPrincipal() instanceof SessionPrincipal principal)
			|| !adultEligibilityService.hasConfirmationForUser(principal.userId())) {
			denyLogin(response, session, next);
			return;
		}

		session.removeAttribute(OAuth2NextRequestFilter.NEXT_SESSION_ATTRIBUTE);
		session.setAttribute(
			AdultEligibilitySessionFilter.SESSION_ATTRIBUTE,
			AdultEligibilitySessionFilter.CONFIRMED_SESSION_VALUE
		);
		response.sendRedirect(frontendOrigin + next);
	}

	private void storeMinimalPendingSignupPrincipal(
		HttpSession session,
		Authentication authentication,
		PendingSignupPrincipal pendingSignup
	) {
		var pendingAuthentication = UsernamePasswordAuthenticationToken.authenticated(
			PendingSignupSessionPrincipal.from(pendingSignup),
			null,
			authentication.getAuthorities()
		);
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(pendingAuthentication);
		SecurityContextHolder.setContext(context);
		session.setAttribute(
			HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
			context
		);
	}

	private String readNext(HttpSession session) {
		if (session == null) {
			return "/";
		}
		Object storedNext = session.getAttribute(OAuth2NextRequestFilter.NEXT_SESSION_ATTRIBUTE);
		return storedNext instanceof String path ? OAuth2RedirectPath.sanitize(path) : "/";
	}

	private void denyLogin(
		HttpServletResponse response,
		HttpSession session,
		String next
	) throws IOException {
		if (session != null) {
			session.invalidate();
		}
		String redirect = frontendOrigin
			+ "/login?reason=signup-eligibility-missing&next="
			+ java.net.URLEncoder.encode(next, java.nio.charset.StandardCharsets.UTF_8);
		response.sendRedirect(redirect);
	}

	private String trimTrailingSlash(String origin) {
		if (origin == null || origin.isBlank()) {
			return "http://localhost:3000";
		}
		return origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin;
	}
}
