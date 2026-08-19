package com.myeongro.api.domain.reading.service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.myeongro.api.domain.reading.dto.CreatedReadingResponse;
import com.myeongro.api.domain.reading.entity.ReadingKind;

@Component
public class ReadingInputRestorer {

	private final Map<ReadingKind, StoredReadingInputRestorer> restorers;

	public ReadingInputRestorer(List<StoredReadingInputRestorer> restorers) {
		Map<ReadingKind, StoredReadingInputRestorer> byKind = new EnumMap<>(ReadingKind.class);
		for (StoredReadingInputRestorer restorer : restorers) {
			if (byKind.put(restorer.kind(), restorer) != null) {
				throw new IllegalStateException("Duplicate reading input restorer: " + restorer.kind());
			}
		}
		if (!byKind.keySet().equals(EnumSet.allOf(ReadingKind.class))) {
			throw new IllegalStateException("A restorer is required for every reading kind");
		}
		this.restorers = Map.copyOf(byKind);
	}

	public NormalizedReadingInput restore(CreatedReadingResponse reading) {
		StoredReadingInputRestorer restorer = restorers.get(reading.kind());
		if (restorer == null) {
			throw new IllegalArgumentException("Unsupported reading kind");
		}
		return restorer.restore(reading);
	}
}
