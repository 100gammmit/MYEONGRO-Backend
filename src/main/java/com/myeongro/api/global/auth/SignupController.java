package com.myeongro.api.global.auth;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.myeongro.api.domain.eligibility.service.SignupCompletionService;
import com.myeongro.api.global.auth.oauth.OAuth2NextRequestFilter;
import com.myeongro.api.global.auth.oauth.OAuthConnectionRevocationException;
import com.myeongro.api.global.auth.oauth.OAuthConnectionRevoker;
import com.myeongro.api.global.auth.oauth.PendingSignupPrincipal;
import com.myeongro.api.global.auth.oauth.ProvisionedOAuthUser;
import com.myeongro.api.global.auth.session.SessionAuthenticatedPrincipal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/signup")
public class SignupController {

	private final SignupCompletionService signupCompletionService;
	private final OAuthConnectionRevoker connectionRevoker;

	public SignupController(
		SignupCompletionService signupCompletionService,
		OAuthConnectionRevoker connectionRevoker
	) {
		this.signupCompletionService = signupCompletionService;
		this.connectionRevoker = connectionRevoker;
	}

	@GetMapping
	public ResponseEntity<Map<String, Object>> status(Authentication authentication) {
		PendingSignupPrincipal pendingSignup = pendingSignup(authentication);
		if (pendingSignup == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		return ResponseEntity.ok(Map.of(
			"pending",
			true,
			"provider",
			pendingSignup.provider()
		));
	}

	@PostMapping("/adult-eligibility")
	public ResponseEntity<Map<String, String>> confirmAdultEligibility(
		Authentication authentication,
		HttpServletRequest request
	) {
		PendingSignupPrincipal pendingSignup = pendingSignup(authentication);
		if (pendingSignup == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		HttpSession session = request.getSession(false);
		if (session == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		ProvisionedOAuthUser user = signupCompletionService.complete(pendingSignup);
		String next = readAndRemoveNext(session);
		var principal = new SessionAuthenticatedPrincipal(
			user.userId(),
			user.displayName(),
			user.provider(),
			user.providerUserId()
		);
		var authenticated = UsernamePasswordAuthenticationToken.authenticated(
			principal,
			null,
			authentication.getAuthorities()
		);
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authenticated);
		SecurityContextHolder.setContext(context);
		session.setAttribute(
			HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
			context
		);
		session.setAttribute(
			AdultEligibilitySessionFilter.SESSION_ATTRIBUTE,
			AdultEligibilitySessionFilter.CONFIRMED_SESSION_VALUE
		);
		return ResponseEntity.ok(Map.of("next", next));
	}

	@DeleteMapping
	public ResponseEntity<Map<String, String>> cancel(
		Authentication authentication,
		HttpServletRequest request
	) {
		PendingSignupPrincipal pendingSignup = pendingSignup(authentication);
		if (pendingSignup == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		boolean revoked = true;
		try {
			connectionRevoker.revoke(
				pendingSignup.provider(),
				pendingSignup.accessToken()
			);
		} catch (OAuthConnectionRevocationException exception) {
			revoked = false;
		} finally {
			HttpSession session = request.getSession(false);
			if (session != null) {
				session.invalidate();
			}
			SecurityContextHolder.clearContext();
		}

		if (!revoked) {
			return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
				"code",
				"OAUTH_UNLINK_FAILED",
				"message",
				"외부 계정 연결 해제를 확인하지 못했습니다. 계정 설정에서 직접 연결을 해제해 주세요."
			));
		}
		return ResponseEntity.noContent().build();
	}

	private PendingSignupPrincipal pendingSignup(Authentication authentication) {
		if (authentication != null
			&& authentication.isAuthenticated()
			&& authentication.getPrincipal() instanceof PendingSignupPrincipal pendingSignup) {
			return pendingSignup;
		}
		return null;
	}

	private String readAndRemoveNext(HttpSession session) {
		Object storedNext = session.getAttribute(OAuth2NextRequestFilter.NEXT_SESSION_ATTRIBUTE);
		session.removeAttribute(OAuth2NextRequestFilter.NEXT_SESSION_ATTRIBUTE);
		return storedNext instanceof String path ? path : "/";
	}
}
