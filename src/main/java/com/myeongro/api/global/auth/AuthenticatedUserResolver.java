package com.myeongro.api.global.auth;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.myeongro.api.global.auth.exception.InvalidAuthenticatedUserException;
import com.myeongro.api.global.auth.exception.UnauthenticatedUserException;

@Component
public class AuthenticatedUserResolver {

	public AuthenticatedUser requireUser(Authentication authentication) {
		if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
			throw new UnauthenticatedUserException();
		}
		try {
			return new AuthenticatedUser(UUID.fromString(jwt.getSubject()));
		} catch (IllegalArgumentException exception) {
			throw new InvalidAuthenticatedUserException(exception);
		}
	}
}
