package com.myeongro.api.global.guest;

import java.time.Instant;
import java.util.UUID;

public record GuestSession(
	UUID sessionId,
	Instant expiresAt
) {
}
