package com.myeongro.api.global.auth.oauth;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

public class PendingOidcUser implements OidcUser, PendingSignupPrincipal, Serializable {

	private static final long serialVersionUID = 1L;

	private final String provider;
	private final String providerUserId;
	private final String displayName;
	private final String email;
	private final String accessToken;
	private final Map<String, Object> claims;
	private final OidcUserInfo userInfo;
	private final OidcIdToken idToken;
	private final Map<String, Object> attributes;
	private final List<GrantedAuthority> authorities;

	public PendingOidcUser(
		OAuthProviderUserInfo providerUserInfo,
		String accessToken,
		OidcUser delegate
	) {
		this.provider = providerUserInfo.provider();
		this.providerUserId = providerUserInfo.providerUserId();
		this.displayName = providerUserInfo.displayName();
		this.email = providerUserInfo.email();
		this.accessToken = accessToken;
		this.claims = Map.copyOf(delegate.getClaims());
		this.userInfo = delegate.getUserInfo();
		this.idToken = delegate.getIdToken();
		this.attributes = Map.copyOf(delegate.getAttributes());
		this.authorities = List.copyOf(delegate.getAuthorities());
	}

	@Override
	public Map<String, Object> getClaims() {
		return claims;
	}

	@Override
	public OidcUserInfo getUserInfo() {
		return userInfo;
	}

	@Override
	public OidcIdToken getIdToken() {
		return idToken;
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
