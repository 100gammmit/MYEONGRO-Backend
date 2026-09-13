package com.myeongro.api.global.auth.oauth;

import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public final class SessionAuthorities {

	private static final List<GrantedAuthority> USER = List.of(
		new SimpleGrantedAuthority("ROLE_USER")
	);

	private SessionAuthorities() {
	}

	public static List<GrantedAuthority> user() {
		return USER;
	}
}
