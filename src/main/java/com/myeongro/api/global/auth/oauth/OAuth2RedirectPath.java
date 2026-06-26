package com.myeongro.api.global.auth.oauth;

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
}
