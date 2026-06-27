package com.myeongro.api.global.auth;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.myeongro.api.global.auth.exception.UnauthenticatedUserException;
import com.myeongro.api.global.auth.session.SessionPrincipal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/auth")
public class AuthController {

	private final AuthenticatedUserResolver userResolver;

	public AuthController(AuthenticatedUserResolver userResolver) {
		this.userResolver = userResolver;
	}

	@GetMapping("/me")
	public ResponseEntity<Map<String, Object>> me(Authentication authentication) {
		if (!(authentication != null
			&& authentication.isAuthenticated()
			&& authentication.getPrincipal() instanceof SessionPrincipal principal)) {
			return ResponseEntity.ok(Map.of("authenticated", false));
		}

		try {
			AuthenticatedUser user = userResolver.requireUser(authentication);
			return ResponseEntity.ok(Map.of(
				"authenticated",
				true,
				"user",
				Map.of(
					"id",
					user.id(),
					"displayName",
					principal.displayName()
				)
			));
		} catch (UnauthenticatedUserException exception) {
			return ResponseEntity.ok(Map.of("authenticated", false));
		}
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		return ResponseEntity.noContent().build();
	}
}
