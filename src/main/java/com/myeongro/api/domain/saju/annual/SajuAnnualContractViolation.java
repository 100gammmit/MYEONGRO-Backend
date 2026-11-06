package com.myeongro.api.domain.saju.annual;

/** Safe diagnostics: codes are developer-defined and never include model text. */
public class SajuAnnualContractViolation extends IllegalArgumentException {
	public enum Code {
		IDENTITY, OVERVIEW_EVIDENCE, INCOMPLETE, MONTH_ORDER, MONTH_EVIDENCE,
		PERIOD_ORDER, PERIOD_EVIDENCE, FABRICATED_QUESTION, SECTION_REQUIRED,
		EVIDENCE, TEXT_REQUIRED, FINISH_REASON, ENVELOPE, OUTPUT_BRANCH, RESULT_TYPE, DECLINE_REASON
	}
	private final Code code;
	public enum EvidenceIssue { NULL, EMPTY, DUPLICATE, UNSUPPORTED, MISSING_REQUIRED }
	private final EvidenceIssue evidenceIssue;
	private final String contractPath;
	public SajuAnnualContractViolation(Code code) {
		super(code.name()); this.code = code; this.evidenceIssue = null; this.contractPath = null;
	}
	/** Only fixed server field paths are permitted; never retain a provider key or free text. */
	public SajuAnnualContractViolation(Code code, EvidenceIssue issue, String path) {
		super(code.name());
		if (issue == null || path == null || !path.matches(
			"(?:annualOverview|questionReading|months\\.m(?:0[1-9]|1[0-2])|periods\\.p[0-9]{2})\\.evidenceKeys")) {
			throw new IllegalArgumentException("Invalid evidence diagnostic");
		}
		this.code = code; this.evidenceIssue = issue; this.contractPath = path;
	}
	public Code code() { return code; }
	public EvidenceIssue evidenceIssue() { return evidenceIssue; }
	public String contractPath() { return contractPath; }
}
