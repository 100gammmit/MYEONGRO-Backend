package com.myeongro.api.global.auth.oauth;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import com.myeongro.api.global.auth.session.SessionPrincipal;

public class SessionOidcUser implements OidcUser, SessionPrincipal, Serializable {

	private final ProvisionedOAuthUser user;
	private final OidcUser delegate;

	public SessionOidcUser(ProvisionedOAuthUser user, OidcUser delegate) {
		this.user = user;
		this.delegate = delegate;
	}

	@Override
	public Map<String, Object> getClaims() {
		return delegate.getClaims();
	}

	@Override
	public OidcUserInfo getUserInfo() {
		return delegate.getUserInfo();
	}

	@Override
	public OidcIdToken getIdToken() {
		return delegate.getIdToken();
	}

	@Override
	public Map<String, Object> getAttributes() {
		return delegate.getAttributes();
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return delegate.getAuthorities();
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
