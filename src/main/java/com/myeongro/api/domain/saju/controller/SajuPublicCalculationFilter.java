package com.myeongro.api.domain.saju.controller;

import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import com.myeongro.api.global.ratelimit.IpRateLimiter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;

/** Installed only in the Security chain, before body buffering and authenticated-account lookups. */
public final class SajuPublicCalculationFilter extends OncePerRequestFilter {
	// Gate, rate limit, account-filter exemptions and authorization share decoded MVC path semantics.
	public static final RequestMatcher CALCULATE = PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST,"/api/saju/calculations");
	public static final RequestMatcher BIRTH_PLACES = PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET,"/api/saju/calculations/birth-places");
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SajuPublicCalculationFilter.class);
	private final boolean enabled;
	private final IpRateLimiter limiter;
	public SajuPublicCalculationFilter(boolean enabled, IpRateLimiter limiter) { this.enabled=enabled; this.limiter=limiter; }
	public static boolean isPublicRequest(HttpServletRequest request) {
		return CALCULATE.matches(request) || BIRTH_PLACES.matches(request);
	}
	@Override protected boolean shouldNotFilter(HttpServletRequest request) { return !isPublicRequest(request); }
	@Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
		response.setHeader("Cache-Control","no-store");
		if (!enabled) { response.setStatus(404); return; }
		if (request.getQueryString()!=null) { error(response,400,"CALCULATION_QUERY_NOT_ALLOWED"); return; }
		if ("POST".equals(request.getMethod())) {
			var decision = limiter.acquire(IpRateLimiter.Scope.SAJU_CALCULATION,request.getRemoteAddr());
			if (decision != IpRateLimiter.Decision.ALLOWED) {
				boolean limited = decision == IpRateLimiter.Decision.LIMITED;
				if (limited) response.setHeader("Retry-After","60");
				String code = limited ? "SAJU_CALCULATION_RATE_LIMITED" : "SAJU_CALCULATION_RATE_LIMIT_UNAVAILABLE";
				logRejection(code);
				error(response,limited ? 429 : 503,code);
				return;
			}
		}
		chain.doFilter(request,response);
	}
	/** CloudWatch metric filters match this line. Never add the IP, request body or birth data to it. */
	static void logRejection(String code) { log.warn("SAJU_CALCULATION_REJECTED code={}", code); }
	private void error(HttpServletResponse response, int status, String code) throws IOException {
		response.setStatus(status); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
		response.getWriter().write("{\"code\":\""+code+"\",\"error\":\"계산 요청을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요.\"}");
	}
}
