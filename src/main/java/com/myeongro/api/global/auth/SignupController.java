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
import com.myeongro.api.global.auth.oauth.PendingSignupSessionPrincipal;
import com.myeongro.api.global.auth.oauth.ProvisionedOAuthUser;
import com.myeongro.api.global.auth.oauth.SessionAuthorities;
import com.myeongro.api.global.auth.oauth.SignupAttemptCoordinator;
import com.myeongro.api.global.auth.oauth.SignupAttemptCoordinator.AttemptState;
import com.myeongro.api.global.auth.oauth.SignupAttemptCoordinator.CompletionClaim;
import com.myeongro.api.global.auth.session.SessionAuthenticatedPrincipal;
import com.myeongro.api.global.auth.session.SessionPrincipal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/signup")
public class SignupController {
	public static final String COMPLETED_NEXT_SESSION_ATTRIBUTE =
		"myeongro.signup.completedNext";

	private final SignupCompletionService signupCompletionService;
	private final OAuthConnectionRevoker connectionRevoker;
	private final SignupAttemptCoordinator signupAttemptCoordinator;

	public SignupController(
		SignupCompletionService signupCompletionService,
		OAuthConnectionRevoker connectionRevoker,
		SignupAttemptCoordinator signupAttemptCoordinator
	) {
		this.signupCompletionService = signupCompletionService;
		this.connectionRevoker = connectionRevoker;
		this.signupAttemptCoordinator = signupAttemptCoordinator;
	}

	@GetMapping
	public ResponseEntity<Map<String, Object>> status(
		Authentication authentication,
		HttpServletRequest request
	) {
		HttpSession session = request.getSession(false);
		String completedNext = completedNext(authentication, session);
		if (completedNext != null) {
			return ResponseEntity.ok(Map.of(
				"pending", false,
				"completed", true,
				"next", completedNext
			));
		}
		if (session == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		PendingSignupSessionPrincipal pendingSignup = pendingSignup(authentication);
		if (pendingSignup == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		AttemptState attemptState = signupAttemptCoordinator.state(
			pendingSignup
		);
		if (attemptState != AttemptState.PENDING) {
			ProvisionedOAuthUser completed = signupCompletionService.findCompleted(
				pendingSignup
			).orElse(null);
			if (completed != null) {
				markCompletedBestEffort(pendingSignup);
				completeSession(session, completed);
				return completedStatus(session);
			}
			if (attemptState == AttemptState.MISSING
				|| attemptState == AttemptState.CANCELLED
				|| attemptState == AttemptState.STALE) {
				return expiredStatus(session);
			}
			return processingStatus();
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
		HttpSession session = request.getSession(false);
		if (session == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		String completedNext = completedNext(authentication, session);
		if (completedNext != null) {
			return ResponseEntity.ok(Map.of("next", completedNext));
		}
		PendingSignupSessionPrincipal pendingSignup = pendingSignup(authentication);
		if (pendingSignup == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		CompletionClaim claim = signupAttemptCoordinator.claimCompletion(
			pendingSignup
		);
		if (claim == CompletionClaim.REJECTED) {
			ProvisionedOAuthUser completed = signupCompletionService.findCompleted(
				pendingSignup
			).orElse(null);
			if (completed != null) {
				markCompletedBestEffort(pendingSignup);
				return completeSession(session, completed);
			}
			AttemptState currentState = signupAttemptCoordinator.state(pendingSignup);
			if (currentState == AttemptState.MISSING || currentState == AttemptState.STALE) {
				return expiredAction(session);
			}
			return conflict();
		}

		ProvisionedOAuthUser user;
		if (claim == CompletionClaim.ALREADY_COMPLETED) {
			user = signupCompletionService.findCompleted(pendingSignup).orElse(null);
			if (user == null) {
				return conflict();
			}
		} else {
			try {
				user = signupCompletionService.complete(pendingSignup);
			} catch (RuntimeException exception) {
				try {
					signupAttemptCoordinator.releaseCompletion(pendingSignup);
				} catch (RuntimeException releaseFailure) {
					exception.addSuppressed(releaseFailure);
				}
				throw exception;
			}
			markCompletedBestEffort(pendingSignup);
		}

		return completeSession(session, user);
	}

	private ResponseEntity<Map<String, String>> completeSession(
		HttpSession session,
		ProvisionedOAuthUser user
	) {
		String next = readNext(session);
		var principal = new SessionAuthenticatedPrincipal(
			user.userId(),
			user.displayName(),
			user.provider(),
			user.providerUserId()
		);
		var authenticated = UsernamePasswordAuthenticationToken.authenticated(
			principal,
			null,
			SessionAuthorities.user()
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
		session.removeAttribute(OAuth2NextRequestFilter.NEXT_SESSION_ATTRIBUTE);
		session.setAttribute(COMPLETED_NEXT_SESSION_ATTRIBUTE, next);
		return ResponseEntity.ok(Map.of("next", next));
	}

	@DeleteMapping
	public ResponseEntity<Map<String, String>> cancel(
		Authentication authentication,
		HttpServletRequest request
	) {
		PendingSignupSessionPrincipal pendingSignup = pendingSignup(authentication);
		if (pendingSignup == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		if (!signupAttemptCoordinator.claimCancellation(pendingSignup)) {
			if (signupCompletionService.findCompleted(pendingSignup).isPresent()) {
				return conflict();
			}
			AttemptState currentState = signupAttemptCoordinator.state(pendingSignup);
			if (currentState == AttemptState.MISSING || currentState == AttemptState.STALE) {
				return expiredAction(request.getSession(false));
			}
			return conflict();
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
			try {
				signupAttemptCoordinator.markCancelled(pendingSignup);
			} catch (RuntimeException ignored) {
				// The claimed cancellation state still blocks completion until TTL expiry.
			} finally {
				HttpSession session = request.getSession(false);
				if (session != null) {
					session.invalidate();
				}
				SecurityContextHolder.clearContext();
			}
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

	private PendingSignupSessionPrincipal pendingSignup(Authentication authentication) {
		if (authentication != null
			&& authentication.isAuthenticated()
			&& authentication.getPrincipal() instanceof PendingSignupSessionPrincipal pendingSignup) {
			return pendingSignup;
		}
		return null;
	}

	private String readNext(HttpSession session) {
		Object storedNext = session.getAttribute(OAuth2NextRequestFilter.NEXT_SESSION_ATTRIBUTE);
		return storedNext instanceof String path ? path : "/";
	}

	private String completedNext(Authentication authentication, HttpSession session) {
		if (session == null
			|| authentication == null
			|| !authentication.isAuthenticated()
			|| !(authentication.getPrincipal() instanceof SessionPrincipal)) {
			return null;
		}
		Object value = session.getAttribute(COMPLETED_NEXT_SESSION_ATTRIBUTE);
		return value instanceof String next ? next : null;
	}

	private ResponseEntity<Map<String, String>> conflict() {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
			"code", "SIGNUP_ATTEMPT_ALREADY_FINALIZING",
			"message", "다른 가입 완료 또는 취소 요청을 처리하고 있습니다. 잠시 후 다시 확인해 주세요."
		));
	}

	private void markCompletedBestEffort(PendingSignupSessionPrincipal pendingSignup) {
		try {
			signupAttemptCoordinator.markCompleted(pendingSignup);
		} catch (RuntimeException ignored) {
			// The committed DB state remains the recovery source if Redis is unavailable.
		}
	}

	private ResponseEntity<Map<String, Object>> completedStatus(HttpSession session) {
		return ResponseEntity.ok(Map.of(
			"pending", false,
			"completed", true,
			"next", session.getAttribute(COMPLETED_NEXT_SESSION_ATTRIBUTE)
		));
	}

	private ResponseEntity<Map<String, Object>> processingStatus() {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
			"code", "SIGNUP_ATTEMPT_IN_PROGRESS",
			"message", "다른 가입 완료 또는 취소 요청을 처리하고 있습니다."
		));
	}

	private ResponseEntity<Map<String, Object>> expiredStatus(HttpSession session) {
		invalidatePendingSession(session);
		return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
			"code", "SIGNUP_ATTEMPT_EXPIRED",
			"message", "가입 대기 시간이 만료되었습니다. 소셜 로그인을 다시 시작해 주세요."
		));
	}

	private ResponseEntity<Map<String, String>> expiredAction(HttpSession session) {
		invalidatePendingSession(session);
		return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
			"code", "SIGNUP_ATTEMPT_EXPIRED",
			"message", "가입 대기 시간이 만료되었습니다. 소셜 로그인을 다시 시작해 주세요."
		));
	}

	private void invalidatePendingSession(HttpSession session) {
		if (session != null) {
			session.invalidate();
		}
		SecurityContextHolder.clearContext();
	}
}
