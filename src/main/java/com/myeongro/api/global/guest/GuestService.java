package com.myeongro.api.global.guest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import com.myeongro.api.global.cookie.CookieService;

/**
 * GuestService
 * <p></p>
 * @author 100minha
 */
@Service
public class GuestService {

	private final CookieService cookieService;
	private final GuestSessionSigner signer;
	private final Clock clock;
	private final Duration ttl;
	private final Supplier<UUID> sessionIdSupplier;

	@Autowired
	public GuestService(
		CookieService cookieService,
		GuestSessionSigner signer,
		@Value("${app.guest.ttl}") Duration ttl
	) {
		this(cookieService, signer, Clock.systemUTC(), ttl, UUID::randomUUID);
	}

	GuestService(
		CookieService cookieService,
		GuestSessionSigner signer,
		Clock clock,
		Duration ttl,
		Supplier<UUID> sessionIdSupplier
	) {
		this.cookieService = cookieService;
		this.signer = signer;
		this.clock = clock;
		this.ttl = ttl;
		this.sessionIdSupplier = sessionIdSupplier;
	}

	public IssuedGuestSession issueGuest() {
		GuestSession session = new GuestSession(
			sessionIdSupplier.get(),
			clock.instant().plus(ttl)
		);
		String token = signer.sign(session.sessionId(), session.expiresAt());
		return new IssuedGuestSession(
			session,
			token,
			cookieService.createGuestCookie(token)
		);
	}

	public ResponseCookie deleteGuestCookie() {
		return cookieService.deleteCookie(CookieService.GUEST_COOKIE_NAME);
	}
}
