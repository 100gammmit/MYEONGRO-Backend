package com.myeongro.api.global.auth.oauth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.web.util.UrlPathHelper;

import jakarta.servlet.http.HttpServletRequest;

final class OAuth2RedirectPath {

	private OAuth2RedirectPath() {
	}

	static String sanitize(String path) {
		if (path == null || path.isBlank()) {
			return "/";
		}
		if (!path.startsWith("/") || path.startsWith("//") || path.contains("\\")) {
			return "/";
		}
		return path;
	}

	// Decoded and relative to the context path, the way Spring's OAuth filters match a request.
	// The raw URI is not safe here: it stays encoded and carries any forwarded prefix.
	static String applicationPath(HttpServletRequest request) {
		return UrlPathHelper.defaultInstance.getPathWithinApplication(request);
	}

	static String frontendOrigin(String origin) {
		if (origin == null || origin.isBlank()) {
			return "http://localhost:3000";
		}
		return origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin;
	}

	static String login(String frontendOrigin, String reason, String next) {
		return frontendOrigin
			+ "/login?reason=" + reason
			+ "&next=" + URLEncoder.encode(next, StandardCharsets.UTF_8);
	}
}
