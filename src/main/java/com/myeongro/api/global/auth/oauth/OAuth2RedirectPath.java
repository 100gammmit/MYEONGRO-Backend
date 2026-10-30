package com.myeongro.api.global.auth.oauth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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
