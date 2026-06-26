package com.myeongro.api.global.auth.oauth;

import java.util.Map;

public interface OAuthProviderUserInfoExtractor {

	boolean supports(String registrationId);

	OAuthProviderUserInfo extract(Map<String, Object> attributes);
}
