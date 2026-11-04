package com.myeongro.api.domain.saju.annual;

/** Safe diagnostics: codes are developer-defined and never include model text. */
public class SajuAnnualContractViolation extends IllegalArgumentException {
	public enum Code {
		IDENTITY, OVERVIEW_EVIDENCE, INCOMPLETE, MONTH_ORDER, MONTH_EVIDENCE,
		PERIOD_ORDER, PERIOD_EVIDENCE, FABRICATED_QUESTION, SECTION_REQUIRED,
		EVIDENCE, TEXT_REQUIRED, FINISH_REASON, ENVELOPE, OUTPUT_BRANCH, RESULT_TYPE, DECLINE_REASON
	}
	private final Code code;
	public SajuAnnualContractViolation(Code code) { super(code.name()); this.code = code; }
	public Code code() { return code; }
}
