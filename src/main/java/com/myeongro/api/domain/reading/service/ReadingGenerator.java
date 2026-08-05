package com.myeongro.api.domain.reading.service;

import java.util.Map;

import com.myeongro.api.domain.reading.dto.GeneratedReading;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.entity.TarotSpreadType;

public interface ReadingGenerator {

	GeneratedReading generate(
		ReadingKind kind,
		TarotSpreadType spreadType,
		String question,
		Map<String, Object> input
	);
}
