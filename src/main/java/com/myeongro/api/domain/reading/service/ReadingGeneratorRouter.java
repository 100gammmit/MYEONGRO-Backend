package com.myeongro.api.domain.reading.service;

import java.util.Map;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.myeongro.api.domain.reading.dto.GeneratedReading;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.entity.TarotSpreadType;
import com.myeongro.api.domain.saju.service.OpenAiSajuReadingGenerator;

@Primary
@Component
public class ReadingGeneratorRouter implements ReadingGenerator {

	private final OpenAiReadingGenerator openAiGenerator;
	private final OpenAiSajuReadingGenerator sajuGenerator;

	public ReadingGeneratorRouter(
		OpenAiReadingGenerator openAiGenerator,
		OpenAiSajuReadingGenerator sajuGenerator
	) {
		this.openAiGenerator = openAiGenerator;
		this.sajuGenerator = sajuGenerator;
	}

	@Override
	public GeneratedReading generate(
		ReadingKind kind,
		TarotSpreadType spreadType,
		String question,
		Map<String, Object> input
	) {
		if (kind == ReadingKind.TAROT) {
			return openAiGenerator.generate(kind, spreadType, question, input);
		}
		return sajuGenerator.generate(kind, spreadType, question, input);
	}
}
