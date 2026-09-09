package com.myeongro.api.global.auth.oauth;

import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class OAuth2NextRequestFilter extends OncePerRequestFilter {

	public static final String NEXT_SESSION_ATTRIBUTE = "myeongro.oauth2.next";
	public static final String ADULT_CONFIRMATION_PARAMETER = "adultEligibility";
	public static final String ADULT_CONFIRMATION_VALUE = "confirmed";
	public static final String ADULT_VERSION_SESSION_ATTRIBUTE =
		"myeongro.oauth2.adultEligibilityVersion";

	private final String adultPolicyVersion;

	public OAuth2NextRequestFilter(String adultPolicyVersion) {
		this.adultPolicyVersion = adultPolicyVersion;
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		if (isOAuthAuthorizationRequest(request)) {
			if (!ADULT_CONFIRMATION_VALUE.equals(
				request.getParameter(ADULT_CONFIRMATION_PARAMETER)
			)) {
				response.sendError(
					HttpServletResponse.SC_FORBIDDEN,
					"Adult eligibility confirmation is required"
				);
				return;
			}
			String next = OAuth2RedirectPath.sanitize(request.getParameter("next"));
			var session = request.getSession(true);
			session.setAttribute(NEXT_SESSION_ATTRIBUTE, next);
			session.setAttribute(ADULT_VERSION_SESSION_ATTRIBUTE, adultPolicyVersion);
		}
		filterChain.doFilter(request, response);
	}

	private boolean isOAuthAuthorizationRequest(HttpServletRequest request) {
		return "GET".equalsIgnoreCase(request.getMethod())
			&& request.getRequestURI().startsWith("/oauth2/authorization/");
	}
}
