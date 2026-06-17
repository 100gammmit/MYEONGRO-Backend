package com.myeongro.api.global.guest;

import org.springframework.http.ResponseCookie;

public record IssuedGuestSession(
	GuestSession session,
	String token,
	ResponseCookie cookie
) {
}
