package com.myeongro.api.global.guest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GuestSessionSignerTests {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final UUID SESSION_ID =
        UUID.fromString("9775ff70-5708-45d8-85f8-cb57878bc25d");
    private static final Instant EXPIRES_AT =
        Instant.parse("2026-06-16T00:00:00.000Z");
    private static final String NEXT_JS_TOKEN =
        "eyJzZXNzaW9uSWQiOiI5Nzc1ZmY3MC01NzA4LTQ1ZDgtODVmOC1jYjU3ODc4YmMyNWQiLCJleHBpcmVzQXQiOiIyMDI2LTA2LTE2VDAwOjAwOjAwLjAwMFoifQ.NoNqcqZd9pEcJyv4nnBnNwhtZzbRcK_M9pg3IRS5TVg";

    private final Clock clock = Clock.fixed(
        Instant.parse("2026-06-15T00:00:00.000Z"),
        ZoneOffset.UTC
    );

    @Test
    void createsTheSameTokenAsTheNextJsImplementation() {
        var signer = new GuestSessionSigner(SECRET, clock);

        assertThat(signer.sign(SESSION_ID, EXPIRES_AT)).isEqualTo(NEXT_JS_TOKEN);
    }

    @Test
    void verifiesATokenCreatedByNextJs() {
        var signer = new GuestSessionSigner(SECRET, clock);

        assertThat(signer.verify(NEXT_JS_TOKEN))
            .contains(new GuestSession(SESSION_ID, EXPIRES_AT));
    }

    @Test
    void rejectsTamperedExpiredAndMalformedTokens() {
        var signer = new GuestSessionSigner(SECRET, clock);
        var expired = signer.sign(
            SESSION_ID,
            Instant.parse("2026-06-14T00:00:00.000Z")
        );

        assertThat(signer.verify(NEXT_JS_TOKEN.substring(0, NEXT_JS_TOKEN.length() - 1) + "x"))
            .isEmpty();
        assertThat(signer.verify(expired)).isEmpty();
        assertThat(signer.verify("not-a-token")).isEmpty();
        assertThat(signer.verify(null)).isEmpty();
    }

    @Test
    void requiresAtLeastThirtyTwoSecretBytes() {
        assertThatThrownBy(() -> new GuestSessionSigner("too-short", clock))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at least 32 bytes");
    }
}
