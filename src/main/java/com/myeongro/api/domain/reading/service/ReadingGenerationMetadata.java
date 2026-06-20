package com.myeongro.api.domain.reading.service;

public record ReadingGenerationMetadata(
	String provider,
	String model,
	String promptVersion
) {
}
