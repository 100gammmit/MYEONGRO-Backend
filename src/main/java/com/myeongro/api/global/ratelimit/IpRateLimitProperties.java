package com.myeongro.api.global.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Positive;

@Validated
@ConfigurationProperties("app.rate-limit")
public record IpRateLimitProperties(
	@Positive int oauthStartPerMinute,
	@Positive int oauthCallbackPerMinute
) {

	int limit(IpRateLimiter.Scope scope) {
		return switch (scope) {
			case OAUTH_START -> oauthStartPerMinute;
			case OAUTH_CALLBACK -> oauthCallbackPerMinute;
		};
	}
}
