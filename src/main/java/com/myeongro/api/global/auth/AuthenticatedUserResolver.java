package com.myeongro.api.global.auth;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.myeongro.api.global.auth.exception.InvalidAuthenticatedUserException;
import com.myeongro.api.global.auth.exception.UnauthenticatedUserException;
import com.myeongro.api.global.auth.session.SessionPrincipal;

@Component
public class AuthenticatedUserResolver {

	public AuthenticatedUser requireUser(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			throw new UnauthenticatedUserException();
		}
		if (authentication.getPrincipal() instanceof SessionPrincipal principal
			&& principal.userId() != null) {
			return new AuthenticatedUser(principal.userId());
		}
		throw new InvalidAuthenticatedUserException();
	}
}
