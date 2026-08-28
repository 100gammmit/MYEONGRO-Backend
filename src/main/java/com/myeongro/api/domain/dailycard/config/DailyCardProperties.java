package com.myeongro.api.domain.dailycard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Validated
@ConfigurationProperties("app.daily-card")
public record DailyCardProperties(
	@NotBlank String contentVersion,
	@Min(1) @Max(20) int variantCount
) {
}
