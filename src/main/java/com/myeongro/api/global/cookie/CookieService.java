package com.myeongro.api.global.cookie;

import org.springframework.http.ResponseCookie;

/**
 * CookieUtil
 * <p></p>
 * @author 100minha
 */

public interface CookieService {

	String GUEST_COOKIE_NAME = "myeongro_guest";

	ResponseCookie createGuestCookie(String token);
	ResponseCookie deleteCookie(String name);
}
