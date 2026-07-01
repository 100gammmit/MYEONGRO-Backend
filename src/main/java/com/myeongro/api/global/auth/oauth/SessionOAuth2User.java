package com.myeongro.api.global.auth.oauth;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import com.myeongro.api.global.auth.session.SessionPrincipal;

public class SessionOAuth2User implements OAuth2User, SessionPrincipal, Serializable {

	private static final long serialVersionUID = 1L;

	private final ProvisionedOAuthUser user;
	private final Map<String, Object> attributes;
	private final List<GrantedAuthority> authorities;

	public SessionOAuth2User(
		ProvisionedOAuthUser user,
		Map<String, Object> attributes,
		Collection<? extends GrantedAuthority> authorities
	) {
		this.user = user;
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
