package com.myeongro.api.domain.saju.calculation;

/** Request-local collector for public-only derived fields, never stored in the legacy snapshot. */
@FunctionalInterface
public interface SajuCandidateObserver {
	SajuCandidateObserver NONE = (candidate, correction) -> { };
	void accept(LunarJavaFourPillarsAdapter.Candidate candidate, TrueSolarTimeCorrector.Correction correction);
}
