package com.myeongro.api.domain.saju.controller;

import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;
import com.myeongro.api.global.ratelimit.IpRateLimiter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;

/** Installed only in the Security chain, before body buffering and authenticated-account lookups. */
public final class SajuPublicCalculationFilter extends OncePerRequestFilter {
	private final boolean enabled;
	private final IpRateLimiter limiter;
	public SajuPublicCalculationFilter(boolean enabled, IpRateLimiter limiter) { this.enabled=enabled; this.limiter=limiter; }
	public static boolean isPublicRequest(HttpServletRequest request) {
		return ("POST".equals(request.getMethod()) && "/api/saju/calculations".equals(request.getRequestURI()))
			|| ("GET".equals(request.getMethod()) && "/api/saju/calculations/birth-places".equals(request.getRequestURI()));
	}
	@Override protected boolean shouldNotFilter(HttpServletRequest request) { return !isPublicRequest(request); }
	@Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
		response.setHeader("Cache-Control","no-store");
		if (!enabled) { response.setStatus(404); return; }
		if (request.getQueryString()!=null) { error(response,400,"CALCULATION_QUERY_NOT_ALLOWED"); return; }
		if ("POST".equals(request.getMethod())) {
			var decision = limiter.acquire(IpRateLimiter.Scope.SAJU_CALCULATION,request.getRemoteAddr());
			if (decision != IpRateLimiter.Decision.ALLOWED) {
				if (decision == IpRateLimiter.Decision.LIMITED) response.setHeader("Retry-After","60");
				error(response,decision == IpRateLimiter.Decision.LIMITED ? 429 : 503,
					decision == IpRateLimiter.Decision.LIMITED ? "SAJU_CALCULATION_RATE_LIMITED" : "SAJU_CALCULATION_RATE_LIMIT_UNAVAILABLE");
				return;
			}
		}
		chain.doFilter(request,response);
	}
	private void error(HttpServletResponse response, int status, String code) throws IOException {
		response.setStatus(status); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
		response.getWriter().write("{\"code\":\""+code+"\",\"error\":\"계산 요청을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요.\"}");
	}
}
