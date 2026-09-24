package com.myeongro.api.global.auth.oauth;

import java.util.Map;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.stereotype.Component;

@Component
public class KakaoOAuthProviderUserInfoExtractor implements OAuthProviderUserInfoExtractor {

	@Override
	public boolean supports(String registrationId) {
		return "kakao".equals(registrationId);
	}

	@Override
	public OAuthProviderUserInfo extract(Map<String, Object> attributes) {
		Object id = attributes.get("id");
		if (id == null) {
			throw new OAuth2AuthenticationException("Kakao user id is missing");
		}
		return new OAuthProviderUserInfo(
			"kakao",
			id.toString()
		);
	}
}
