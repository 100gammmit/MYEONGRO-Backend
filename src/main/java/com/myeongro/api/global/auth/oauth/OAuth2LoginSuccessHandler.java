package com.myeongro.api.global.auth.oauth;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

	private final String frontendOrigin;

	public OAuth2LoginSuccessHandler(String frontendOrigin) {
		this.frontendOrigin = trimTrailingSlash(frontendOrigin);
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
		response.sendRedirect(frontendOrigin + next);
	}

	private String trimTrailingSlash(String origin) {
		if (origin == null || origin.isBlank()) {
			return "http://localhost:3000";
		}
		return origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin;
	}
}
