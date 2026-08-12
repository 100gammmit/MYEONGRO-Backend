package com.myeongro.api.domain.reading.exception;

import java.util.UUID;

public class OpenAiReadingGenerationException extends RuntimeException {

	private final String code;
	private final OpenAiReadingGenerationStage stage;
	private final String causeType;
	private final UUID readingId;

	public OpenAiReadingGenerationException() {
		this(OpenAiReadingGenerationStage.UNKNOWN, null);
	}

	public OpenAiReadingGenerationException(
		OpenAiReadingGenerationStage stage,
		Throwable cause
	) {
		this(stage, cause, null);
	}

	private OpenAiReadingGenerationException(
		OpenAiReadingGenerationStage stage,
		Throwable cause,
		UUID readingId
	) {
		super("OpenAI reading generation failed", cause);
		this.code = "OPENAI_READING_GENERATION_FAILED";
		this.stage = stage;
		this.causeType = cause == null ? "unknown" : cause.getClass().getSimpleName();
		this.readingId = readingId;
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

	public UUID getReadingId() {
		return readingId;
	}

	public OpenAiReadingGenerationException withReadingId(UUID failedReadingId) {
		return new OpenAiReadingGenerationException(stage, getCause(), failedReadingId);
	}
}
