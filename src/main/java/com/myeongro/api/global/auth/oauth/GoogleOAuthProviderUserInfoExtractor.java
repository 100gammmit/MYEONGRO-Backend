package com.myeongro.api.global.auth.oauth;

import java.util.Map;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Component;

@Component
public class GoogleOAuthProviderUserInfoExtractor implements OAuthProviderUserInfoExtractor {

	@Override
	public boolean supports(String registrationId) {
		return "google".equals(registrationId);
	}

	@Override
	public OAuthProviderUserInfo extract(Map<String, Object> attributes) {
		Object subject = attributes.get("sub");
		if (subject == null) {
			throw new OAuth2AuthenticationException(new OAuth2Error(
				"invalid_google_user_info",
				"Google user subject is missing",
				null
			));
		}
		return new OAuthProviderUserInfo(
			"google",
			subject.toString()
		);
	}
}
