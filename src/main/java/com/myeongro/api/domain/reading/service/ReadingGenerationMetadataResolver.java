package com.myeongro.api.domain.reading.service;

import org.springframework.stereotype.Component;

import com.myeongro.api.domain.reading.entity.ReadingKind;

@Component
public class ReadingGenerationMetadataResolver {

	private final ReadingGeneratorRouter generatorRouter;

	public ReadingGenerationMetadataResolver(ReadingGeneratorRouter generatorRouter) {
		this.generatorRouter = generatorRouter;
	}

	public ReadingGenerationMetadata resolve(ReadingKind kind, String spreadType) {
		return generatorRouter.metadata(kind, spreadType);
	}
}
