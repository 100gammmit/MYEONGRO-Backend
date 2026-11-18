package com.myeongro.api.domain.saju.calculation;

/**
 * Request-local collector for public-only derived fields, never stored in the legacy snapshot.
 *
 * <p>Contract: an observer must keep only what every candidate shares, so feeding a value it has already seen
 * changes nothing. The unknown-time search relies on this. It calls {@link #accept} once per run of equal
 * candidates, in the original minute/offset order, with the correction of the run's first minute, not once per
 * minute. On that path the luck cycle is null, so an observer must not derive minute-level values from the
 * correction when {@code candidate.luckCycle()} is null, and must not count calls as candidates.
 */
@FunctionalInterface
public interface SajuCandidateObserver {
	SajuCandidateObserver NONE = (candidate, correction) -> { };
	void accept(LunarJavaFourPillarsAdapter.Candidate candidate, TrueSolarTimeCorrector.Correction correction);
}
