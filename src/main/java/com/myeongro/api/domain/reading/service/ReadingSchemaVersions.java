package com.myeongro.api.domain.reading.service;

import com.myeongro.api.domain.reading.entity.ReadingKind;

public final class ReadingSchemaVersions {

	public static final int TAROT = 1;
	public static final int SAJU = 3;
	private static final int SAJU_PROVINCE_CITY = 2;

	private ReadingSchemaVersions() {
	}

	public static int current(ReadingKind kind) {
		return switch (kind) {
			case TAROT -> TAROT;
			case SAJU -> SAJU;
		};
	}

	public static boolean supports(ReadingKind kind, int schemaVersion) {
		return schemaVersion == current(kind)
			|| kind == ReadingKind.SAJU && schemaVersion == SAJU_PROVINCE_CITY;
	}
}
