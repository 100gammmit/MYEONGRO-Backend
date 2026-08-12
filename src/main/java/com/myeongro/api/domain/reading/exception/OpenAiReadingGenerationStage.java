package com.myeongro.api.domain.reading.exception;

public enum OpenAiReadingGenerationStage {

	REQUEST_BUILD,
	PROVIDER_CALL,
	RESPONSE_PARSE,
	RESPONSE_CONTRACT,
	UNKNOWN
}
