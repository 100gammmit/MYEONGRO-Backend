package com.myeongro.api.aipreview;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.myeongro.api.domain.reading.entity.ReadingKind;

final class AiPreviewLabelFactory {

	private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private final Clock clock;

	AiPreviewLabelFactory(Clock clock) {
		this.clock = clock;
	}

	String create(ReadingKind kind) {
		return FORMATTER.format(LocalDateTime.now(clock)) + "-" + kind.value();
	}
}
