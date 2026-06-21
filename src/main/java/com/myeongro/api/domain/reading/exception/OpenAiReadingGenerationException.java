package com.myeongro.api.domain.reading.exception;

public class OpenAiReadingGenerationException extends RuntimeException {

	private final String code;

	public OpenAiReadingGenerationException() {
		super("OpenAI reading generation failed");
		this.code = "OPENAI_READING_GENERATION_FAILED";
	}

	public String getCode() {
		return code;
	}
}
