package com.myeongro.api.domain.reading.exception;

public class OpenAiReadingGenerationException extends RuntimeException {

	private final String code;
	private final OpenAiReadingGenerationStage stage;
	private final String causeType;

	public OpenAiReadingGenerationException() {
		this(OpenAiReadingGenerationStage.UNKNOWN, null);
	}

	public OpenAiReadingGenerationException(
		OpenAiReadingGenerationStage stage,
		Throwable cause
	) {
		super("OpenAI reading generation failed");
		this.code = "OPENAI_READING_GENERATION_FAILED";
		this.stage = stage;
		this.causeType = cause == null ? "unknown" : cause.getClass().getSimpleName();
	}

	public String getCode() {
		return code;
	}

	public OpenAiReadingGenerationStage getStage() {
		return stage;
	}

	public String getCauseType() {
		return causeType;
	}
}
