package com.myeongro.api.domain.reading.service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.myeongro.api.domain.reading.dto.GeneratedReading;
import com.myeongro.api.domain.reading.entity.ReadingKind;

@Primary
@Component
public class ReadingGeneratorRouter implements ReadingGenerator {

	private final Map<ReadingKind, ReadingGenerationHandler> handlers;

	public ReadingGeneratorRouter(List<ReadingGenerationHandler> handlers) {
		Map<ReadingKind, ReadingGenerationHandler> byKind = new EnumMap<>(ReadingKind.class);
		for (ReadingGenerationHandler handler : handlers) {
			if (byKind.put(handler.kind(), handler) != null) {
				throw new IllegalStateException("Duplicate reading generation handler: " + handler.kind());
			}
		}
		if (byKind.size() != ReadingKind.values().length) {
			throw new IllegalStateException("A generation handler is required for every reading kind");
		}
		this.handlers = Map.copyOf(byKind);
	}

	@Override
	public GeneratedReading generate(
		ReadingKind kind,
		String spreadType,
		String question,
		Map<String, Object> input
	) {
		return handler(kind).generate(kind, spreadType, question, input);
	}

	public ReadingGenerationMetadata metadata(ReadingKind kind, String spreadType) {
		return handler(kind).metadata(spreadType);
	}

	private ReadingGenerationHandler handler(ReadingKind kind) {
		ReadingGenerationHandler handler = handlers.get(kind);
		if (handler == null) {
			throw new IllegalArgumentException("Unsupported reading kind");
		}
		return handler;
	}
}
