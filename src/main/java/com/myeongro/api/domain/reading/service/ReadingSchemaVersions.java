package com.myeongro.api.domain.reading.service;

import com.myeongro.api.domain.reading.entity.ReadingKind;

public final class ReadingSchemaVersions {

	public static final int TAROT = 2;
	public static final int SAJU = 5;

	private ReadingSchemaVersions() {
	}

	public static int current(ReadingKind kind) {
		return switch (kind) {
			case TAROT -> TAROT;
			case SAJU -> SAJU;
		};
	}

}
