package com.myeongro.api.domain.profile.repository;

import com.myeongro.api.global.auth.oauth.OAuthProviderUserInfo;

public interface OAuthAccountLock {

	void lock(OAuthProviderUserInfo userInfo);
}
