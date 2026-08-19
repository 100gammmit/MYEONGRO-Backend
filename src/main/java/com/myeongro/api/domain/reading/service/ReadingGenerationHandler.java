package com.myeongro.api.domain.reading.service;

import com.myeongro.api.domain.reading.entity.ReadingKind;

public interface ReadingGenerationHandler extends ReadingGenerator {

	ReadingKind kind();

	ReadingGenerationMetadata metadata(String spreadType);
}
