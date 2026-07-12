package com.myeongro.api.domain.reading.service;

import java.util.Map;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.myeongro.api.domain.reading.dto.ReadingResult;
import com.myeongro.api.domain.reading.entity.ReadingKind;

@Primary
@Component
public class ReadingGeneratorRouter implements ReadingGenerator {

	private final OpenAiReadingGenerator openAiGenerator;
	private final DemoReadingGenerator demoGenerator;

	public ReadingGeneratorRouter(
		OpenAiReadingGenerator openAiGenerator,
		DemoReadingGenerator demoGenerator
	) {
		this.openAiGenerator = openAiGenerator;
		this.demoGenerator = demoGenerator;
	}

	@Override
	public ReadingResult generate(
		ReadingKind kind,
		String question,
		Map<String, Object> input
	) {
		if (kind == ReadingKind.TAROT) {
			return openAiGenerator.generate(kind, question, input);
		}
		return demoGenerator.generate(kind, question, input);
	}
}
