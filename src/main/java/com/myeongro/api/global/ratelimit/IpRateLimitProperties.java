package com.myeongro.api.global.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Positive;

@Validated
@ConfigurationProperties("app.rate-limit")
public record IpRateLimitProperties(
	@Positive int oauthStartPerMinute,
	@Positive int oauthCallbackPerMinute,
	@org.springframework.boot.context.properties.bind.DefaultValue("20") @Positive int sajuCalculationPerMinute
) {
	@org.springframework.boot.context.properties.bind.ConstructorBinding
	public IpRateLimitProperties { }
	public IpRateLimitProperties(int oauthStartPerMinute, int oauthCallbackPerMinute) { this(oauthStartPerMinute,oauthCallbackPerMinute,20); }

	int limit(IpRateLimiter.Scope scope) {
		return switch (scope) {
			case OAUTH_START -> oauthStartPerMinute;
			case OAUTH_CALLBACK -> oauthCallbackPerMinute;
			case SAJU_CALCULATION -> sajuCalculationPerMinute;
		};
	}
}
