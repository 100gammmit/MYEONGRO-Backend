package com.myeongro.api.domain.reading.service;

import java.util.Map;

import com.myeongro.api.domain.reading.dto.ReadingResult;
import com.myeongro.api.domain.reading.entity.ReadingKind;

public interface ReadingGenerator {

	ReadingResult generate(ReadingKind kind, String question, Map<String, Object> input);
}
