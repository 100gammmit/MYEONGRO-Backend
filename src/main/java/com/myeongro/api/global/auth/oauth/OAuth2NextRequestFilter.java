package com.myeongro.api.global.auth.oauth;

import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class OAuth2NextRequestFilter extends OncePerRequestFilter {

	public static final String NEXT_SESSION_ATTRIBUTE = "myeongro.oauth2.next";

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		if (isOAuthAuthorizationRequest(request)) {
			String next = OAuth2RedirectPath.sanitize(request.getParameter("next"));
			request.getSession(true).setAttribute(NEXT_SESSION_ATTRIBUTE, next);
		}
		filterChain.doFilter(request, response);
	}

	private boolean isOAuthAuthorizationRequest(HttpServletRequest request) {
		return "GET".equalsIgnoreCase(request.getMethod())
			&& request.getRequestURI().startsWith("/oauth2/authorization/");
	}
}
