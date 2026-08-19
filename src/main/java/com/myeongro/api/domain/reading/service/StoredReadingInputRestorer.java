package com.myeongro.api.domain.reading.service;

import com.myeongro.api.domain.reading.dto.CreatedReadingResponse;
import com.myeongro.api.domain.reading.entity.ReadingKind;

public interface StoredReadingInputRestorer {

	ReadingKind kind();

	NormalizedReadingInput restore(CreatedReadingResponse reading);
}
