package com.myeongro.api.global.auth.oauth;

import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;

import com.myeongro.api.global.ratelimit.IpRateLimiter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Runs ahead of {@link OAuth2NextRequestFilter} so a rejected request never creates a session.
 * These are browser navigations, so rejection is a redirect rather than a JSON 429.
 */
public class OAuth2RateLimitFilter extends OncePerRequestFilter {

	private final String frontendOrigin;
	private final IpRateLimiter ipRateLimiter;

	public OAuth2RateLimitFilter(String frontendOrigin, IpRateLimiter ipRateLimiter) {
		this.frontendOrigin = OAuth2RedirectPath.frontendOrigin(frontendOrigin);
		this.ipRateLimiter = ipRateLimiter;
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		IpRateLimiter.Scope scope = scope(OAuth2RedirectPath.applicationPath(request));
		if (scope == null || ipRateLimiter.tryAcquire(scope, request.getRemoteAddr())) {
			filterChain.doFilter(request, response);
			return;
		}
		response.sendRedirect(OAuth2RedirectPath.login(
			frontendOrigin,
			"rate-limited",
			OAuth2RedirectPath.sanitize(request.getParameter("next"))
		));
	}

	private IpRateLimiter.Scope scope(String path) {
		if (path.startsWith("/oauth2/authorization/")) {
			return IpRateLimiter.Scope.OAUTH_START;
		}
		if (path.startsWith("/login/oauth2/code/")) {
			return IpRateLimiter.Scope.OAUTH_CALLBACK;
		}
		return null;
	}
}
