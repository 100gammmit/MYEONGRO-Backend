package com.myeongro.api.global.cookie;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

/**
 * LocalCookieUtil
 * <p></p>
 * @author 100minha
 */

@Service
public class LocalCookieService implements CookieService {

	private final Duration guestCookieTtl;
	private final boolean secure;

	public LocalCookieService(
		@Value("${app.guest.ttl}") Duration guestCookieTtl,
		@Value("${app.guest.cookie-secure:true}") boolean secure
	) {
		this.guestCookieTtl = guestCookieTtl;
		this.secure = secure;
	}

	@Override
	public ResponseCookie createGuestCookie(String token) {
		return ResponseCookie.from(GUEST_COOKIE_NAME, token)
			.httpOnly(true)
			.secure(secure)
			.sameSite("Lax")
			.path("/")
			.maxAge(guestCookieTtl)
			.build();
	}

	@Override
	public ResponseCookie deleteCookie(String name) {
		return ResponseCookie.from(name, "")
			.httpOnly(true)
			.secure(secure)
			.sameSite("Lax")
			.path("/")
			.maxAge(Duration.ZERO)
			.build();
	}
}
