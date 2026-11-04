package com.myeongro.api.domain.saju.model;

import com.myeongro.api.domain.reading.service.ReadingInputSupport;

public enum SajuReportType {
	BIRTH_ANNUAL_QUESTION("birth_annual_question", 6), ANNUAL_MONTHLY("annual_monthly", 7);
	private final String value;
	private final int schemaVersion;
	SajuReportType(String value, int schemaVersion) { this.value = value; this.schemaVersion = schemaVersion; }
	public String value() { return value; }
	public int schemaVersion() { return schemaVersion; }
	public static SajuReportType fromValue(String value) {
		if (value == null || BIRTH_ANNUAL_QUESTION.value.equals(value)) return BIRTH_ANNUAL_QUESTION;
		if (ANNUAL_MONTHLY.value.equals(value)) return ANNUAL_MONTHLY;
		throw ReadingInputSupport.invalid("INVALID_REPORT_TYPE", "reportType", "리딩 종류를 확인해 주세요.");
	}
}
