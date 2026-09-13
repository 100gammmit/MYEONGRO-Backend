package com.myeongro.api.global.auth;

import java.io.IOException;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.myeongro.api.global.auth.oauth.PendingSignupPrincipal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class PendingSignupAccessFilter extends OncePerRequestFilter {

	private static final Set<String> ALLOWED_API_PATHS = Set.of(
		"/api/auth/me",
		"/api/signup",
		"/api/signup/adult-eligibility"
	);

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null
			&& authentication.isAuthenticated()
			&& authentication.getPrincipal() instanceof PendingSignupPrincipal
			&& request.getRequestURI().startsWith("/api/")
			&& !ALLOWED_API_PATHS.contains(request.getRequestURI())) {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}
		filterChain.doFilter(request, response);
	}
}
