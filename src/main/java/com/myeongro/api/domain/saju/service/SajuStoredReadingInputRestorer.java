package com.myeongro.api.domain.saju.service;

import org.springframework.stereotype.Component;

import com.myeongro.api.domain.reading.dto.CreatedReadingResponse;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.service.NormalizedReadingInput;
import com.myeongro.api.domain.reading.service.StoredReadingInputRestorer;

@Component
public class SajuStoredReadingInputRestorer implements StoredReadingInputRestorer {

	private final SajuReadingInputNormalizer normalizer;
	private final SajuReadingInputAssembler assembler;

	public SajuStoredReadingInputRestorer(
		SajuReadingInputNormalizer normalizer,
		SajuReadingInputAssembler assembler
	) {
		this.normalizer = normalizer;
		this.assembler = assembler;
	}

	@Override
	public ReadingKind kind() {
		return ReadingKind.SAJU;
	}

	@Override
	public NormalizedReadingInput restore(CreatedReadingResponse reading) {
		return assembler.restore(normalizer.restoreBase(reading), reading.input());
	}
}
