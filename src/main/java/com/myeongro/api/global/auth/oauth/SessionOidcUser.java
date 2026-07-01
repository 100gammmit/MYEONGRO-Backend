package com.myeongro.api.global.auth.oauth;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import com.myeongro.api.global.auth.session.SessionPrincipal;

public class SessionOidcUser implements OidcUser, SessionPrincipal, Serializable {

	private static final long serialVersionUID = 1L;

	private final ProvisionedOAuthUser user;
	private final Map<String, Object> claims;
	private final OidcUserInfo userInfo;
	private final OidcIdToken idToken;
	private final Map<String, Object> attributes;
	private final List<GrantedAuthority> authorities;

	public SessionOidcUser(ProvisionedOAuthUser user, OidcUser delegate) {
		this.user = user;
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
		return user.userId().toString();
	}

	@Override
	public UUID userId() {
		return user.userId();
	}

	@Override
	public String displayName() {
		return user.displayName();
	}

	@Override
	public String provider() {
		return user.provider();
	}

	@Override
	public String providerUserId() {
		return user.providerUserId();
	}
}
