package com.myeongro.api.global.cookie;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class LocalCookieServiceTests {

    @Test
    void createsAndDeletesTheGuestCookieWithTheExpectedContract() {
        var service = new LocalCookieService(Duration.ofDays(30), true);

        var created = service.createGuestCookie("signed-token");
        var deleted = service.deleteCookie(CookieService.GUEST_COOKIE_NAME);

        assertThat(created.getName()).isEqualTo("myeongro_guest");
        assertThat(created.getValue()).isEqualTo("signed-token");
        assertThat(created.isHttpOnly()).isTrue();
        assertThat(created.isSecure()).isTrue();
        assertThat(created.getSameSite()).isEqualTo("Lax");
        assertThat(created.getPath()).isEqualTo("/");
        assertThat(created.getMaxAge()).isEqualTo(Duration.ofDays(30));

        assertThat(deleted.getValue()).isEmpty();
        assertThat(deleted.getMaxAge()).isEqualTo(Duration.ZERO);
    }

    @Test
    void canCreateHttpOnlyNonSecureCookiesForLocalHttpDevelopment() {
        var service = new LocalCookieService(Duration.ofDays(30), false);

        var created = service.createGuestCookie("signed-token");

        assertThat(created.isHttpOnly()).isTrue();
        assertThat(created.isSecure()).isFalse();
    }
}
