package com.myeongro.api.domain.tarotdraw.service;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.tarot.draw-session")
public record TarotDrawSessionProperties(String namespace, Duration ttl) {

	public TarotDrawSessionProperties {
		if (namespace == null || namespace.isBlank()) {
			throw new IllegalArgumentException("Tarot draw namespace is required");
		}
		if (ttl == null || ttl.isZero() || ttl.isNegative()) {
			throw new IllegalArgumentException("Tarot draw TTL must be positive");
		}
	}
}
