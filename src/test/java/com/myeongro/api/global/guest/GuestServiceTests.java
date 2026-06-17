package com.myeongro.api.global.guest;

import static org.assertj.core.api.Assertions.assertThat;

import com.myeongro.api.global.cookie.LocalCookieService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GuestServiceTests {

    @Test
    void issuesAVerifiableGuestSessionAndCookie() {
        var now = Instant.parse("2026-06-15T00:00:00.000Z");
        var clock = Clock.fixed(now, ZoneOffset.UTC);
        var ttl = Duration.ofDays(30);
        var signer = new GuestSessionSigner(
            "0123456789abcdef0123456789abcdef",
            clock
        );
        var service = new GuestService(
            new LocalCookieService(ttl, true),
            signer,
            clock,
            ttl,
            () -> UUID.fromString("9775ff70-5708-45d8-85f8-cb57878bc25d")
        );

        var issued = service.issueGuest();

        assertThat(issued.session()).isEqualTo(
            new GuestSession(
                UUID.fromString("9775ff70-5708-45d8-85f8-cb57878bc25d"),
                now.plus(ttl)
            )
        );
        assertThat(signer.verify(issued.token())).contains(issued.session());
        assertThat(issued.cookie().getValue()).isEqualTo(issued.token());
    }
}
