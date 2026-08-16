package com.myeongro.api.domain.saju.calculation;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.myeongro.api.domain.saju.calculation.LunarJavaFourPillarsAdapter.Candidate;
import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot.AnnualFortune;
import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot.LuckCycle;
import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot.Pillar;
import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot.Pillars;
import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot.Relation;
import com.myeongro.api.domain.saju.model.LuckDirectionBasis;

@Component
public class ApproximateBirthTimeResolver {

	private final TrueSolarTimeCorrector corrector;
	private final LunarJavaFourPillarsAdapter adapter;

	public ApproximateBirthTimeResolver(
		TrueSolarTimeCorrector corrector,
		LunarJavaFourPillarsAdapter adapter
	) {
		this.corrector = corrector;
		this.adapter = adapter;
	}

	public Resolution resolve(
		LocalDateTime rememberedTime,
		double longitude,
		LuckDirectionBasis luckDirectionBasis,
		int targetYear
	) {
		LocalDateTime start = rememberedTime.minusMinutes(SajuCalculationRules.APPROXIMATE_MINUTES);
		LocalDateTime end = rememberedTime.plusMinutes(SajuCalculationRules.APPROXIMATE_MINUTES);
		CandidateSet candidateSet = candidatesForRange(
			start, end, longitude, true, luckDirectionBasis, targetYear
		);
		List<SajuLimitationCode> limitations = new ArrayList<>();
		limitations.add(SajuLimitationCode.APPROXIMATE_BIRTH_TIME);
		addDstLimitations(candidateSet, limitations);
		return merge(
			candidateSet, start, end, "approximate", true, limitations
		);
	}

	public Resolution resolveExact(
		List<TrueSolarTimeCorrector.Correction> corrections,
		LuckDirectionBasis luckDirectionBasis,
		int targetYear
	) {
		if (corrections.isEmpty()) {
			throw new IllegalArgumentException("Civil time does not exist");
		}
		List<Candidate> candidates = corrections.stream()
			.map(correction -> adapter.calculate(
				correction.trueSolarTime(), correction.engineCivilTime(), true,
				luckDirectionBasis, targetYear
			))
			.toList();
		CandidateSet candidateSet = new CandidateSet(
			candidates,
			corrections.stream().map(TrueSolarTimeCorrector.Correction::zoneOffset)
				.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)),
			false,
			corrections.size() > 1
		);
		List<SajuLimitationCode> limitations = new ArrayList<>();
		addDstLimitations(candidateSet, limitations);
		LocalDateTime civilTime = corrections.getFirst().civilTime();
		return merge(candidateSet, civilTime, civilTime, "exact", true, limitations);
	}

	public Resolution resolveUnknown(
		LocalDate birthDate,
		double longitude,
		LuckDirectionBasis luckDirectionBasis,
		int targetYear
	) {
		LocalDateTime start = birthDate.atStartOfDay();
		LocalDateTime end = birthDate.atTime(23, 59);
		CandidateSet candidateSet = candidatesForRange(
			start, end, longitude, false, luckDirectionBasis, targetYear
		);
		List<SajuLimitationCode> limitations = new ArrayList<>();
		addDstLimitations(candidateSet, limitations);
		return merge(candidateSet, start, end, "unknown", false, limitations);
	}

	private Resolution merge(
		CandidateSet candidateSet,
		LocalDateTime start,
		LocalDateTime end,
		String precision,
		boolean includeTime,
		List<SajuLimitationCode> limitations
	) {
		List<Candidate> candidates = candidateSet.candidates();
		if (candidates.isEmpty()) {
			throw new IllegalArgumentException("No valid civil-time candidate");
		}
		Pillar year = commonPillar(candidates, candidate -> candidate.pillars().year());
		Pillar month = commonPillar(candidates, candidate -> candidate.pillars().month());
		Pillar day = commonPillar(candidates, candidate -> candidate.pillars().day());
		Pillar time = includeTime
			? commonPillar(candidates, candidate -> candidate.pillars().time())
			: null;
		String dayMaster = common(candidates, Candidate::dayMaster);
		LuckCycle luckCycle = includeTime ? common(candidates, Candidate::luckCycle) : null;
		AnnualFortune annual = commonAnnualFortune(candidates);
		Map<String, Integer> elements = commonElementCounts(candidates);
		List<Relation> relations = commonRelations(candidates);

		List<String> varying = new ArrayList<>();
		addVariation(year, "pillars.year", SajuLimitationCode.YEAR_PILLAR_UNCERTAIN, varying, limitations);
		addVariation(month, "pillars.month", SajuLimitationCode.MONTH_PILLAR_UNCERTAIN, varying, limitations);
		addVariation(day, "pillars.day", SajuLimitationCode.DAY_PILLAR_UNCERTAIN, varying, limitations);
		if (includeTime) {
			addVariation(time, "pillars.time", SajuLimitationCode.TIME_PILLAR_UNCERTAIN, varying, limitations);
		}
		if (includeTime && luckCycle == null
			&& candidates.stream().anyMatch(candidate -> candidate.luckCycle() != null)) {
			varying.add("luckCycle");
			limitations.add(SajuLimitationCode.LUCK_CYCLE_UNCERTAIN);
		}
		if (annual != null && annual.stemTenGod() == null) {
			varying.add("annualFortune.stemTenGod");
		}
		return new Resolution(
			new Candidate(new Pillars(year, month, day, time), dayMaster, elements, relations, luckCycle, annual),
			List.copyOf(limitations),
			new SajuCalculationSnapshot.Uncertainty(
				precision, candidates.size(), start.toString(), end.toString(), varying,
				List.copyOf(candidateSet.zoneOffsets())
			)
		);
	}

	private CandidateSet candidatesForRange(
		LocalDateTime start,
		LocalDateTime end,
		double longitude,
		boolean includeTime,
		LuckDirectionBasis luckDirectionBasis,
		int targetYear
	) {
		List<Candidate> candidates = new ArrayList<>();
		Set<String> offsets = new LinkedHashSet<>();
		boolean gap = false;
		boolean overlap = false;
		for (LocalDateTime cursor = start; !cursor.isAfter(end); cursor = cursor.plusMinutes(1)) {
			List<TrueSolarTimeCorrector.Correction> corrections =
				corrector.correctCandidates(cursor, longitude);
			gap |= corrections.isEmpty();
			overlap |= corrections.size() > 1;
			for (TrueSolarTimeCorrector.Correction correction : corrections) {
				offsets.add(correction.zoneOffset());
				candidates.add(adapter.calculate(
					correction.trueSolarTime(), correction.engineCivilTime(), includeTime,
					luckDirectionBasis, targetYear
				));
			}
		}
		return new CandidateSet(candidates, offsets, gap, overlap);
	}

	private void addDstLimitations(
		CandidateSet candidateSet,
		List<SajuLimitationCode> limitations
	) {
		if (candidateSet.gap()) {
			limitations.add(SajuLimitationCode.DST_GAP_SKIPPED);
		}
		if (candidateSet.overlap()) {
			limitations.add(SajuLimitationCode.DST_OVERLAP_AMBIGUOUS);
		}
	}

	private <T> T common(List<Candidate> candidates, Function<Candidate, T> getter) {
		T first = getter.apply(candidates.getFirst());
		return candidates.stream().allMatch(candidate -> Objects.equals(first, getter.apply(candidate)))
			? first
			: null;
	}

	private AnnualFortune commonAnnualFortune(List<Candidate> candidates) {
		List<AnnualFortune> values = candidates.stream()
			.map(Candidate::annualFortune)
			.toList();
		if (values.stream().anyMatch(Objects::isNull)) {
			return null;
		}
		Integer year = commonValue(values, AnnualFortune::year);
		String ganZhi = commonValue(values, AnnualFortune::ganZhi);
		if (year == null || ganZhi == null) {
			return null;
		}
		return new AnnualFortune(
			year,
			ganZhi,
			commonValue(values, AnnualFortune::stemTenGod)
		);
	}

	private Pillar commonPillar(
		List<Candidate> candidates,
		Function<Candidate, Pillar> getter
	) {
		List<Pillar> pillars = candidates.stream().map(getter).toList();
		Pillar first = pillars.getFirst();
		if (first == null || pillars.stream().anyMatch(Objects::isNull)) {
			return null;
		}
		String ganZhi = commonValue(pillars, Pillar::ganZhi);
		if (ganZhi == null) {
			return null;
		}
		List<String> branchTenGods = commonValue(pillars, Pillar::branchTenGods);
		return new Pillar(
			ganZhi,
			commonValue(pillars, Pillar::stem),
			commonValue(pillars, Pillar::branch),
			commonValue(pillars, Pillar::fiveElements),
			commonValue(pillars, Pillar::stemTenGod),
			branchTenGods == null ? List.of() : branchTenGods
		);
	}

	private <T, R> R commonValue(List<T> values, Function<T, R> getter) {
		R first = getter.apply(values.getFirst());
		return values.stream().allMatch(value -> Objects.equals(first, getter.apply(value)))
			? first
			: null;
	}

	private Map<String, Integer> commonElementCounts(List<Candidate> candidates) {
		Map<String, Integer> first = candidates.getFirst().fiveElements();
		Map<String, Integer> common = new LinkedHashMap<>();
		for (Map.Entry<String, Integer> entry : first.entrySet()) {
			if (candidates.stream().allMatch(candidate ->
				Objects.equals(entry.getValue(), candidate.fiveElements().get(entry.getKey())))) {
				common.put(entry.getKey(), entry.getValue());
			}
		}
		return common;
	}

	private List<Relation> commonRelations(List<Candidate> candidates) {
		Set<Relation> common = new LinkedHashSet<>(candidates.getFirst().relations());
		for (Candidate candidate : candidates.subList(1, candidates.size())) {
			common.retainAll(candidate.relations());
		}
		return List.copyOf(common);
	}

	private void addVariation(
		Pillar value,
		String field,
		SajuLimitationCode code,
		List<String> varying,
		List<SajuLimitationCode> limitations
	) {
		if (value == null) {
			varying.add(field);
			limitations.add(code);
		}
	}

	public record Resolution(
		Candidate trusted,
		List<SajuLimitationCode> limitations,
		SajuCalculationSnapshot.Uncertainty uncertainty
	) {
		public Resolution {
			limitations = List.copyOf(limitations);
		}
	}

	private record CandidateSet(
		List<Candidate> candidates,
		Set<String> zoneOffsets,
		boolean gap,
		boolean overlap
	) {
		private CandidateSet {
			candidates = List.copyOf(candidates);
			zoneOffsets = java.util.Collections.unmodifiableSet(
				new LinkedHashSet<>(zoneOffsets)
			);
		}
	}
}
