package com.myeongro.api.global.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import com.myeongro.api.domain.eligibility.service.SignupCompletionService;
import com.myeongro.api.global.auth.oauth.OAuth2NextRequestFilter;
import com.myeongro.api.global.auth.oauth.OAuthConnectionRevocationException;
import com.myeongro.api.global.auth.oauth.OAuthConnectionRevoker;
import com.myeongro.api.global.auth.oauth.PendingSignupSessionPrincipal;
import com.myeongro.api.global.auth.oauth.ProvisionedOAuthUser;
import com.myeongro.api.global.auth.oauth.SignupAttemptCoordinator;
import com.myeongro.api.global.auth.oauth.SignupAttemptCoordinator.Attempt;
import com.myeongro.api.global.auth.oauth.SignupAttemptCoordinator.AttemptState;
import com.myeongro.api.global.auth.oauth.SignupAttemptCoordinator.CompletionClaim;
import com.myeongro.api.global.auth.session.SessionAuthenticatedPrincipal;
import com.myeongro.api.global.auth.session.SessionPrincipal;

class SignupControllerTests {

	private static final UUID USER_ID =
		UUID.fromString("43bc72f9-eed1-4e4b-8717-6fe969b4ea43");
	private static final ProvisionedOAuthUser COMPLETED_USER = new ProvisionedOAuthUser(
		USER_ID, "명로 사용자", "kakao", "12345"
	);
	private final SignupCompletionService signupCompletionService =
		org.mockito.Mockito.mock(SignupCompletionService.class);
	private final OAuthConnectionRevoker connectionRevoker =
		org.mockito.Mockito.mock(OAuthConnectionRevoker.class);
	private final SignupAttemptCoordinator attemptCoordinator =
		org.mockito.Mockito.mock(SignupAttemptCoordinator.class);
	private final SignupController controller = new SignupController(
		signupCompletionService, connectionRevoker, attemptCoordinator
	);

	@BeforeEach
	void preparePendingAttempt() {
		when(attemptCoordinator.state(org.mockito.ArgumentMatchers.any()))
			.thenReturn(AttemptState.PENDING);
	}

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void exposesOnlyThePendingProviderInSignupStatus() {
		var response = controller.status(pendingAuthentication(), requestWithSession());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).containsEntry("pending", true);
		assertThat(response.getBody()).containsEntry("provider", "kakao");
	}

	@Test
	void completesAccountCreationAndKeepsAnIdempotentCompletionResult() {
		Authentication authentication = pendingAuthentication();
		PendingSignupSessionPrincipal pending = pendingPrincipal(authentication);
		MockHttpServletRequest request = requestWithSession();
		MockHttpSession session = (MockHttpSession) request.getSession(false);
		session.setAttribute(OAuth2NextRequestFilter.NEXT_SESSION_ATTRIBUTE, "/records?tab=latest");
		when(attemptCoordinator.claimCompletion(pending))
			.thenReturn(CompletionClaim.ACQUIRED);
		when(signupCompletionService.complete(pending)).thenReturn(COMPLETED_USER);

		var response = controller.confirmAdultEligibility(authentication, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).containsEntry("next", "/records?tab=latest");
		assertThat(session.getAttribute(SignupController.COMPLETED_NEXT_SESSION_ATTRIBUTE))
			.isEqualTo("/records?tab=latest");
		assertThat(session.getAttribute(AdultEligibilitySessionFilter.SESSION_ATTRIBUTE))
			.isEqualTo(AdultEligibilitySessionFilter.CONFIRMED_SESSION_VALUE);
		SecurityContext context = securityContext(session);
		assertThat(context.getAuthentication().getPrincipal()).isInstanceOf(SessionPrincipal.class);
		assertThat(((SessionPrincipal) context.getAuthentication().getPrincipal()).userId())
			.isEqualTo(USER_ID);
		assertThat(context.getAuthentication().getAuthorities())
			.allMatch(authority -> authority.getClass().equals(
				org.springframework.security.core.authority.SimpleGrantedAuthority.class
			));
		assertThat(new String(serialize(context), StandardCharsets.ISO_8859_1))
			.doesNotContain("source-sensitive-value");

		var replay = controller.confirmAdultEligibility(context.getAuthentication(), request);
		assertThat(replay.getBody()).containsEntry("next", "/records?tab=latest");
		verify(signupCompletionService, times(1)).complete(pending);
		verify(attemptCoordinator).markCompleted(pending);
	}

	@Test
	void recoversACompletedAttemptBeforeTheSessionUpgradeWasObserved() {
		Authentication authentication = pendingAuthentication();
		PendingSignupSessionPrincipal pending = pendingPrincipal(authentication);
		MockHttpServletRequest request = requestWithSession();
		request.getSession(false).setAttribute(
			OAuth2NextRequestFilter.NEXT_SESSION_ATTRIBUTE, "/account"
		);
		when(attemptCoordinator.claimCompletion(pending))
			.thenReturn(CompletionClaim.ALREADY_COMPLETED);
		when(signupCompletionService.findCompleted(pending))
			.thenReturn(Optional.of(COMPLETED_USER));

		var response = controller.confirmAdultEligibility(authentication, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).containsEntry("next", "/account");
		assertThat(securityContext((MockHttpSession) request.getSession(false))
			.getAuthentication().getPrincipal()).isInstanceOf(SessionPrincipal.class);
		verify(signupCompletionService, never()).complete(pending);
	}

	@Test
	void releasesTheCompletionClaimWhenTheDatabaseTransactionFails() {
		Authentication authentication = pendingAuthentication();
		PendingSignupSessionPrincipal pending = pendingPrincipal(authentication);
		when(attemptCoordinator.claimCompletion(pending))
			.thenReturn(CompletionClaim.ACQUIRED);
		when(signupCompletionService.complete(pending))
			.thenThrow(new IllegalStateException("database unavailable"));

		assertThatThrownBy(() -> controller.confirmAdultEligibility(
			authentication, requestWithSession()
		)).isInstanceOf(IllegalStateException.class);

		verify(attemptCoordinator).releaseCompletion(pending);
	}

	@Test
	void completesTheSessionEvenWhenTheRedisCompletedTransitionFails() {
		Authentication authentication = pendingAuthentication();
		PendingSignupSessionPrincipal pending = pendingPrincipal(authentication);
		when(attemptCoordinator.claimCompletion(pending))
			.thenReturn(CompletionClaim.ACQUIRED);
		when(signupCompletionService.complete(pending)).thenReturn(COMPLETED_USER);
		doThrow(new IllegalStateException("redis unavailable"))
			.when(attemptCoordinator).markCompleted(pending);

		var response = controller.confirmAdultEligibility(authentication, requestWithSession());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		verify(signupCompletionService).complete(pending);
	}

	@Test
	void recoversACommittedSignupFromAStuckCompletingAttemptOnPostRetry() {
		Authentication authentication = pendingAuthentication();
		PendingSignupSessionPrincipal pending = pendingPrincipal(authentication);
		when(attemptCoordinator.claimCompletion(pending))
			.thenReturn(CompletionClaim.REJECTED);
		when(attemptCoordinator.state(pending)).thenReturn(AttemptState.COMPLETING);
		when(signupCompletionService.findCompleted(pending))
			.thenReturn(Optional.of(COMPLETED_USER));

		var response = controller.confirmAdultEligibility(authentication, requestWithSession());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).containsEntry("next", "/");
		verify(signupCompletionService, never()).complete(pending);
		verify(attemptCoordinator).markCompleted(pending);
	}

	@Test
	void recoversACommittedSignupFromAStuckCompletingAttemptOnStatusReload() {
		Authentication authentication = pendingAuthentication();
		PendingSignupSessionPrincipal pending = pendingPrincipal(authentication);
		MockHttpServletRequest request = requestWithSession();
		when(attemptCoordinator.state(pending)).thenReturn(AttemptState.COMPLETING);
		when(signupCompletionService.findCompleted(pending))
			.thenReturn(Optional.of(COMPLETED_USER));

		var response = controller.status(authentication, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).containsEntry("completed", true);
		assertThat(securityContext((MockHttpSession) request.getSession(false))
			.getAuthentication().getPrincipal()).isInstanceOf(SessionPrincipal.class);
	}

	@Test
	void expiresALivePendingSessionWhenItsAttemptKeyIsMissing() {
		Authentication authentication = pendingAuthentication();
		PendingSignupSessionPrincipal pending = pendingPrincipal(authentication);
		MockHttpServletRequest request = requestWithSession();
		MockHttpSession session = (MockHttpSession) request.getSession(false);
		when(attemptCoordinator.state(pending)).thenReturn(AttemptState.MISSING);
		when(signupCompletionService.findCompleted(pending)).thenReturn(Optional.empty());

		var response = controller.status(authentication, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
		assertThat(response.getBody()).containsEntry("code", "SIGNUP_ATTEMPT_EXPIRED");
		assertThat(session.isInvalid()).isTrue();
	}

	@Test
	void expiresCompletionAndCancellationWhenTheAttemptKeyIsMissing() {
		Authentication authentication = pendingAuthentication();
		PendingSignupSessionPrincipal pending = pendingPrincipal(authentication);
		when(attemptCoordinator.claimCompletion(pending))
			.thenReturn(CompletionClaim.REJECTED);
		when(attemptCoordinator.claimCancellation(pending)).thenReturn(false);
		when(attemptCoordinator.state(pending)).thenReturn(AttemptState.MISSING);
		when(signupCompletionService.findCompleted(pending)).thenReturn(Optional.empty());

		var completion = controller.confirmAdultEligibility(
			authentication, requestWithSession()
		);
		var cancellation = controller.cancel(authentication, requestWithSession());

		assertThat(completion.getStatusCode()).isEqualTo(HttpStatus.GONE);
		assertThat(cancellation.getStatusCode()).isEqualTo(HttpStatus.GONE);
		verify(connectionRevoker, never()).revoke("kakao", "access-token");
	}

	@Test
	void doesNotRecoverAStaleCancelledGenerationFromANewerSignupCompletion() {
		Authentication staleAuthentication = pendingAuthentication(
			"attempt-old", "generation-cancelled"
		);
		PendingSignupSessionPrincipal stale = pendingPrincipal(staleAuthentication);
		MockHttpServletRequest request = requestWithSession();
		MockHttpSession session = (MockHttpSession) request.getSession(false);
		when(attemptCoordinator.state(stale)).thenReturn(AttemptState.STALE);
		when(signupCompletionService.findCompleted(stale)).thenReturn(Optional.empty());

		var response = controller.status(staleAuthentication, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
		assertThat(session.isInvalid()).isTrue();
		verify(signupCompletionService).findCompleted(stale);
	}

	@Test
	void exposesTheCompletionResultAfterAResponseLossAndPageReload() {
		MockHttpServletRequest request = requestWithSession();
		request.getSession(false).setAttribute(
			SignupController.COMPLETED_NEXT_SESSION_ATTRIBUTE, "/records"
		);
		Authentication completed = UsernamePasswordAuthenticationToken.authenticated(
			new SessionAuthenticatedPrincipal(USER_ID, "명로 사용자", "kakao", "12345"),
			null,
			List.of()
		);

		var response = controller.status(completed, request);

		assertThat(response.getBody()).containsEntry("completed", true);
		assertThat(response.getBody()).containsEntry("next", "/records");
	}

	@Test
	void revokesTheProviderConnectionAndInvalidatesThePendingSessionOnCancel() {
		Authentication authentication = pendingAuthentication();
		PendingSignupSessionPrincipal pending = pendingPrincipal(authentication);
		MockHttpServletRequest request = requestWithSession();
		MockHttpSession session = (MockHttpSession) request.getSession(false);
		when(attemptCoordinator.claimCancellation(pending)).thenReturn(true);

		var response = controller.cancel(authentication, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		verify(connectionRevoker).revoke("kakao", "access-token");
		verify(attemptCoordinator).markCancelled(pending);
		assertThat(session.isInvalid()).isTrue();
	}

	@Test
	void removesThePendingSessionEvenWhenProviderUnlinkFails() {
		Authentication authentication = pendingAuthentication();
		PendingSignupSessionPrincipal pending = pendingPrincipal(authentication);
		MockHttpServletRequest request = requestWithSession();
		MockHttpSession session = (MockHttpSession) request.getSession(false);
		when(attemptCoordinator.claimCancellation(pending)).thenReturn(true);
		doThrow(new OAuthConnectionRevocationException("kakao", new RuntimeException()))
			.when(connectionRevoker).revoke("kakao", "access-token");

		var response = controller.cancel(authentication, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
		assertThat(response.getBody()).containsEntry("code", "OAUTH_UNLINK_FAILED");
		assertThat(session.isInvalid()).isTrue();
	}

	@Test
	void completionFromOneAttemptPreventsSameIdentityCancellationFromAnotherAttempt()
		throws Exception {
		InMemoryAttemptCoordinator coordinator = new InMemoryAttemptCoordinator();
		SignupController concurrentController = new SignupController(
			signupCompletionService, connectionRevoker, coordinator
		);
		CountDownLatch completionStarted = new CountDownLatch(1);
		CountDownLatch releaseCompletion = new CountDownLatch(1);
		when(signupCompletionService.complete(org.mockito.ArgumentMatchers.any()))
			.thenAnswer(invocation -> {
				completionStarted.countDown();
				releaseCompletion.await(5, TimeUnit.SECONDS);
				return COMPLETED_USER;
			});
		MockHttpServletRequest completionRequest = requestWithSession();
		completionRequest.getSession(false).setAttribute(
			OAuth2NextRequestFilter.NEXT_SESSION_ATTRIBUTE, "/records"
		);
		ExecutorService executor = Executors.newSingleThreadExecutor();
		Future<?> completion = executor.submit(() -> concurrentController.confirmAdultEligibility(
			pendingAuthentication("attempt-a", "generation-shared"), completionRequest
		));
		assertThat(completionStarted.await(5, TimeUnit.SECONDS)).isTrue();

		var cancellation = concurrentController.cancel(
			pendingAuthentication("attempt-b", "generation-shared"),
			requestSharingSession((MockHttpSession) completionRequest.getSession(false))
		);

		assertThat(cancellation.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		verify(connectionRevoker, never()).revoke("kakao", "access-token");
		releaseCompletion.countDown();
		completion.get(5, TimeUnit.SECONDS);
		executor.shutdownNow();
	}

	@Test
	void cancellationFromOneAttemptPreventsSameIdentityCompletionFromAnotherAttempt()
		throws Exception {
		InMemoryAttemptCoordinator coordinator = new InMemoryAttemptCoordinator();
		SignupController concurrentController = new SignupController(
			signupCompletionService, connectionRevoker, coordinator
		);
		CountDownLatch cancellationStarted = new CountDownLatch(1);
		CountDownLatch releaseCancellation = new CountDownLatch(1);
		doAnswer(invocation -> {
			cancellationStarted.countDown();
			releaseCancellation.await(5, TimeUnit.SECONDS);
			return null;
		}).when(connectionRevoker).revoke("kakao", "access-token");
		MockHttpServletRequest cancellationRequest = requestWithSession();
		ExecutorService executor = Executors.newSingleThreadExecutor();
		Future<?> cancellation = executor.submit(() -> concurrentController.cancel(
			pendingAuthentication("attempt-a", "generation-shared"), cancellationRequest
		));
		assertThat(cancellationStarted.await(5, TimeUnit.SECONDS)).isTrue();

		var completion = concurrentController.confirmAdultEligibility(
			pendingAuthentication("attempt-b", "generation-shared"),
			requestSharingSession((MockHttpSession) cancellationRequest.getSession(false))
		);

		assertThat(completion.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		verify(signupCompletionService, never()).complete(
			org.mockito.ArgumentMatchers.any()
		);
		releaseCancellation.countDown();
		cancellation.get(5, TimeUnit.SECONDS);
		executor.shutdownNow();
	}

	@Test
	void rejectsSignupActionsWithoutAPendingOauthPrincipal() {
		Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
			"guest", null, List.of()
		);

		assertThat(controller.status(authentication, new MockHttpServletRequest()).getStatusCode())
			.isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(controller.confirmAdultEligibility(
			authentication, new MockHttpServletRequest()
		).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	private Authentication pendingAuthentication() {
		return pendingAuthentication("attempt-1", "generation-1");
	}

	private Authentication pendingAuthentication(String attemptId, String generationId) {
		var principal = new PendingSignupSessionPrincipal(
			attemptId, generationId, "kakao", "12345", "명로 사용자",
			"user@example.com", "access-token"
		);
		return UsernamePasswordAuthenticationToken.authenticated(
			principal,
			null,
			List.of(new OAuth2UserAuthority(java.util.Map.of(
				"source-sensitive-claim", "source-sensitive-value"
			)))
		);
	}

	private PendingSignupSessionPrincipal pendingPrincipal(Authentication authentication) {
		return (PendingSignupSessionPrincipal) authentication.getPrincipal();
	}

	private MockHttpServletRequest requestWithSession() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.getSession(true);
		return request;
	}

	private MockHttpServletRequest requestSharingSession(MockHttpSession session) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setSession(session);
		return request;
	}

	private SecurityContext securityContext(MockHttpSession session) {
		return (SecurityContext) session.getAttribute(
			HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY
		);
	}

	private byte[] serialize(Object value) {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
				output.writeObject(value);
			}
			return bytes.toByteArray();
		} catch (java.io.IOException exception) {
			throw new AssertionError(exception);
		}
	}

	private static final class InMemoryAttemptCoordinator implements SignupAttemptCoordinator {
		private String state = "pending";
		private String generationId = "generation-shared";
		private String ownerAttemptId;

		@Override
		public Attempt beginAttempt(String provider, String providerUserId) {
			return new Attempt(UUID.randomUUID().toString(), generationId);
		}

		@Override
		public synchronized AttemptState state(PendingSignupSessionPrincipal principal) {
			if (!generationId.equals(principal.attemptGenerationId())) {
				return AttemptState.STALE;
			}
			return switch (state) {
				case "pending" -> AttemptState.PENDING;
				case "completing" -> AttemptState.COMPLETING;
				case "completed" -> AttemptState.COMPLETED;
				case "cancelling" -> AttemptState.CANCELLING;
				case "cancelled" -> AttemptState.CANCELLED;
				default -> AttemptState.MISSING;
			};
		}

		@Override
		public synchronized CompletionClaim claimCompletion(PendingSignupSessionPrincipal principal) {
			if (!generationId.equals(principal.attemptGenerationId())) {
				return CompletionClaim.REJECTED;
			}
			if (state.equals("completed")) return CompletionClaim.ALREADY_COMPLETED;
			if (!state.equals("pending")) return CompletionClaim.REJECTED;
			state = "completing";
			ownerAttemptId = principal.attemptId();
			return CompletionClaim.ACQUIRED;
		}

		@Override
		public synchronized boolean claimCancellation(PendingSignupSessionPrincipal principal) {
			if (!generationId.equals(principal.attemptGenerationId())) return false;
			if (!state.equals("pending")) return false;
			state = "cancelling";
			ownerAttemptId = principal.attemptId();
			return true;
		}

		@Override
		public synchronized void markCompleted(PendingSignupSessionPrincipal principal) {
			if (state.equals("completing") && principal.attemptId().equals(ownerAttemptId)) {
				state = "completed";
			}
		}

		@Override
		public synchronized void releaseCompletion(PendingSignupSessionPrincipal principal) {
			if (state.equals("completing") && principal.attemptId().equals(ownerAttemptId)) {
				state = "pending";
				ownerAttemptId = null;
			}
		}

		@Override
		public synchronized void markCancelled(PendingSignupSessionPrincipal principal) {
			if (state.equals("cancelling") && principal.attemptId().equals(ownerAttemptId)) {
				state = "cancelled";
			}
		}
	}
}
