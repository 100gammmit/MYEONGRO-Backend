package com.myeongro.api.global.auth.oauth;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

public class PendingOAuth2User implements OAuth2User, PendingSignupPrincipal, Serializable {

	private static final long serialVersionUID = 1L;

	private final String provider;
	private final String providerUserId;
	private final String displayName;
	private final String email;
	private final String accessToken;
	private final Map<String, Object> attributes;
	private final List<GrantedAuthority> authorities;

	public PendingOAuth2User(
		OAuthProviderUserInfo userInfo,
		String accessToken,
		Map<String, Object> attributes,
		Collection<? extends GrantedAuthority> authorities
	) {
		this.provider = userInfo.provider();
		this.providerUserId = userInfo.providerUserId();
		this.displayName = userInfo.displayName();
		this.email = userInfo.email();
		this.accessToken = accessToken;
		this.attributes = Map.copyOf(attributes);
		this.authorities = List.copyOf(authorities);
	}

	@Override
	public Map<String, Object> getAttributes() {
		return attributes;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return authorities;
	}

	@Override
	public String getName() {
		return provider + ":" + providerUserId;
	}

	@Override
	public String provider() {
		return provider;
	}

	@Override
	public String providerUserId() {
		return providerUserId;
	}

	@Override
	public String displayName() {
		return displayName;
	}

	@Override
	public String email() {
		return email;
	}

	@Override
	public String accessToken() {
		return accessToken;
	}
}
