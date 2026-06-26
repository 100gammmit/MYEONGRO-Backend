package com.myeongro.api.global.auth.session;

import java.util.UUID;

public interface SessionPrincipal {

	UUID userId();

	String displayName();

	String provider();

	String providerUserId();
}
