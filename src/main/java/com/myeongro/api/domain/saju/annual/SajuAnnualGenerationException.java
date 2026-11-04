package com.myeongro.api.domain.saju.annual;

import java.util.Map;
import com.myeongro.api.domain.reading.exception.OpenAiReadingGenerationException;
import com.myeongro.api.domain.reading.exception.OpenAiReadingGenerationStage;

/** Carries token counts, never response text, so Preview can account for rejected output too. */
public class SajuAnnualGenerationException extends OpenAiReadingGenerationException {
	private final Map<String, Object> metrics;
	public SajuAnnualGenerationException(Exception cause, Map<String, Object> metrics) {
		super(OpenAiReadingGenerationStage.RESPONSE_CONTRACT, cause);
		this.metrics = Map.copyOf(metrics);
	}
	public Map<String, Object> metrics() { return metrics; }
}
