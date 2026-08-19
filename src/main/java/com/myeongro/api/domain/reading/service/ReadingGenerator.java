package com.myeongro.api.domain.reading.service;

import java.util.Map;

import com.myeongro.api.domain.reading.dto.GeneratedReading;
import com.myeongro.api.domain.reading.entity.ReadingKind;

public interface ReadingGenerator {

	GeneratedReading generate(
		ReadingKind kind,
		String spreadType,
		String question,
		Map<String, Object> input
	);
}
