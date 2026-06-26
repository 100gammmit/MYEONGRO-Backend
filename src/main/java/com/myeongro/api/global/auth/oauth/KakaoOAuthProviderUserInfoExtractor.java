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
			id.toString(),
			nestedString(attributes, "properties", "nickname"),
			nestedString(attributes, "kakao_account", "email")
		);
	}

	@SuppressWarnings("unchecked")
	private String nestedString(Map<String, Object> attributes, String objectName, String key) {
		Object nested = attributes.get(objectName);
		if (!(nested instanceof Map<?, ?> map)) {
			return null;
		}
		Object value = ((Map<String, Object>) map).get(key);
		return value == null ? null : value.toString();
	}
}
