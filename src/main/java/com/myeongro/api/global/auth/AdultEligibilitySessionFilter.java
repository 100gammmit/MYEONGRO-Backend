package com.myeongro.api.global.auth;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.myeongro.api.global.auth.session.SessionPrincipal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Component
public class AdultEligibilitySessionFilter extends OncePerRequestFilter {

	public static final String SESSION_ATTRIBUTE = "myeongro.adultEligibilityVersion";

	private final String currentVersion;

	public AdultEligibilitySessionFilter(
		@Value("${app.eligibility.adult-policy-version}") String currentVersion
	) {
		this.currentVersion = currentVersion;
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null
			&& authentication.isAuthenticated()
			&& authentication.getPrincipal() instanceof SessionPrincipal
			&& !hasCurrentConfirmation(request.getSession(false))) {
			SecurityContextHolder.clearContext();
			HttpSession session = request.getSession(false);
			if (session != null) {
				session.invalidate();
			}
		}
		filterChain.doFilter(request, response);
	}

	private boolean hasCurrentConfirmation(HttpSession session) {
		return session != null
			&& currentVersion.equals(session.getAttribute(SESSION_ATTRIBUTE));
	}
}
