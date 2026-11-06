package com.myeongro.api.domain.saju.annual;

import java.util.Map;
import com.myeongro.api.domain.reading.exception.OpenAiReadingGenerationException;
import com.myeongro.api.domain.reading.exception.OpenAiReadingGenerationStage;

/** Carries token counts, never response text, so Preview can account for rejected output too. */
public class SajuAnnualGenerationException extends OpenAiReadingGenerationException {
	private final Map<String, Object> metrics;
	public SajuAnnualGenerationException(Exception cause, Map<String, Object> metrics) {
		// Jackson exception messages/causes can retain response excerpts and echoed free text.
		super(OpenAiReadingGenerationStage.RESPONSE_CONTRACT,
			new IllegalArgumentException("Annual response rejected: " + safeCode(cause)));
		var measured = new java.util.LinkedHashMap<>(metrics);
		measured.put("contractCode", safeCode(cause));
		if (cause instanceof SajuAnnualContractViolation violation && violation.evidenceIssue() != null) {
			measured.put("evidenceIssue", violation.evidenceIssue().name());
			measured.put("contractPath", violation.contractPath());
		}
		this.metrics = Map.copyOf(measured);
	}
	public Map<String, Object> metrics() { return metrics; }
	private static String safeCode(Exception cause) {
		return cause instanceof SajuAnnualContractViolation violation
			? violation.code().name() : cause.getClass().getSimpleName();
	}
}
