package com.myeongro.api.domain.saju.controller;

import java.util.Arrays;
import java.util.List;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * CORS for the two public manseryeok paths only. The browser calls the API domain directly without cookies, so the
 * IP rate limit sees the real client. Every other path stays same-origin through the Next proxy.
 * Installed before {@link SajuPublicCalculationFilter} so its 404, 429 and 503 responses are readable cross-origin
 * and preflights never reach the rate limit.
 */
public final class SajuPublicCalculationCors {

	private SajuPublicCalculationCors() {
	}

	public static CorsFilter filter(String allowedOrigins) {
		var configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(origins(allowedOrigins));
		configuration.setAllowedMethods(List.of("GET", "POST"));
		configuration.setAllowedHeaders(List.of("Content-Type"));
		configuration.setExposedHeaders(List.of("Retry-After"));
		configuration.setAllowCredentials(false);
		configuration.setMaxAge(600L);
		var source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/saju/calculations", configuration);
		source.registerCorsConfiguration("/api/saju/calculations/birth-places", configuration);
		return new CorsFilter(source);
	}

	static List<String> origins(String allowedOrigins) {
		List<String> origins = Arrays.stream(allowedOrigins == null ? new String[0] : allowedOrigins.split(","))
			.map(String::trim)
			.map(origin -> origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin)
			.filter(origin -> !origin.isEmpty())
			.toList();
		if (origins.isEmpty() || origins.contains("*")) {
			throw new IllegalStateException("app.saju.public-calculation.allowed-origins must list explicit origins");
		}
		return origins;
	}
}
