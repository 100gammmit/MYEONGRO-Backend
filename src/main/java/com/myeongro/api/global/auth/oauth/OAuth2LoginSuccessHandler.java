package com.myeongro.api.global.auth.oauth;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

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
		String next = "/";
		if (session != null) {
			Object storedNext = session.getAttribute(OAuth2NextRequestFilter.NEXT_SESSION_ATTRIBUTE);
			session.removeAttribute(OAuth2NextRequestFilter.NEXT_SESSION_ATTRIBUTE);
			if (storedNext instanceof String path) {
				next = OAuth2RedirectPath.sanitize(path);
			}
		}
		if (!(authentication.getPrincipal() instanceof SessionPrincipal principal)
			|| session == null) {
			denyLogin(response, session, next);
			return;
		}

		Object confirmedVersion = session.getAttribute(
			OAuth2NextRequestFilter.ADULT_VERSION_SESSION_ATTRIBUTE
		);
		if (!(confirmedVersion instanceof String version)
			|| !adultEligibilityService.isCurrentVersion(version)) {
			denyLogin(response, session, next);
			return;
		}

		adultEligibilityService.confirmCurrentForUser(principal.userId(), version);
		session.removeAttribute(OAuth2NextRequestFilter.ADULT_VERSION_SESSION_ATTRIBUTE);
		session.setAttribute(AdultEligibilitySessionFilter.SESSION_ATTRIBUTE, version);
		response.sendRedirect(frontendOrigin + next);
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
			+ "/login?reason=adult-eligibility-required&next="
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
