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
		List<Candidate> candidates = new ArrayList<>();
		for (LocalDateTime cursor = start; !cursor.isAfter(end); cursor = cursor.plusMinutes(1)) {
			LocalDateTime corrected = corrector.correct(cursor, longitude).trueSolarTime();
			candidates.add(adapter.calculate(corrected, true, luckDirectionBasis, targetYear));
		}
		return merge(candidates, start, end);
	}

	public Resolution resolveUnknown(
		LocalDate birthDate,
		double longitude,
		LuckDirectionBasis luckDirectionBasis,
		int targetYear
	) {
		LocalDateTime start = birthDate.atStartOfDay();
		LocalDateTime end = birthDate.atTime(23, 59);
		List<Candidate> candidates = new ArrayList<>(24 * 60);
		for (LocalDateTime cursor = start; !cursor.isAfter(end); cursor = cursor.plusMinutes(1)) {
			LocalDateTime corrected = corrector.correct(cursor, longitude).trueSolarTime();
			candidates.add(adapter.calculate(corrected, false, luckDirectionBasis, targetYear));
		}
		Pillar year = commonPillar(candidates, candidate -> candidate.pillars().year());
		Pillar month = commonPillar(candidates, candidate -> candidate.pillars().month());
		Pillar day = commonPillar(candidates, candidate -> candidate.pillars().day());
		String dayMaster = common(candidates, Candidate::dayMaster);
		AnnualFortune annual = common(candidates, Candidate::annualFortune);
		List<String> varying = new ArrayList<>();
		List<SajuLimitationCode> limitations = new ArrayList<>();
		addVariation(year, "pillars.year", SajuLimitationCode.YEAR_PILLAR_UNCERTAIN, varying, limitations);
		addVariation(month, "pillars.month", SajuLimitationCode.MONTH_PILLAR_UNCERTAIN, varying, limitations);
		addVariation(day, "pillars.day", SajuLimitationCode.DAY_PILLAR_UNCERTAIN, varying, limitations);
		if (annual == null) {
			varying.add("annualFortune");
		}
		return new Resolution(
			new Candidate(
				new Pillars(year, month, day, null), dayMaster,
				commonElementCounts(candidates), commonRelations(candidates), null, annual
			),
			limitations,
			new SajuCalculationSnapshot.Uncertainty(
				"unknown", candidates.size(), start.toString(), end.toString(), varying
			)
		);
	}

	private Resolution merge(
		List<Candidate> candidates,
		LocalDateTime start,
		LocalDateTime end
	) {
		Pillar year = commonPillar(candidates, candidate -> candidate.pillars().year());
		Pillar month = commonPillar(candidates, candidate -> candidate.pillars().month());
		Pillar day = commonPillar(candidates, candidate -> candidate.pillars().day());
		Pillar time = commonPillar(candidates, candidate -> candidate.pillars().time());
		String dayMaster = common(candidates, Candidate::dayMaster);
		LuckCycle luckCycle = common(candidates, Candidate::luckCycle);
		AnnualFortune annual = common(candidates, Candidate::annualFortune);
		Map<String, Integer> elements = commonElementCounts(candidates);
		List<Relation> relations = commonRelations(candidates);

		List<String> varying = new ArrayList<>();
		List<SajuLimitationCode> limitations = new ArrayList<>();
		limitations.add(SajuLimitationCode.APPROXIMATE_BIRTH_TIME);
		addVariation(year, "pillars.year", SajuLimitationCode.YEAR_PILLAR_UNCERTAIN, varying, limitations);
		addVariation(month, "pillars.month", SajuLimitationCode.MONTH_PILLAR_UNCERTAIN, varying, limitations);
		addVariation(day, "pillars.day", SajuLimitationCode.DAY_PILLAR_UNCERTAIN, varying, limitations);
		addVariation(time, "pillars.time", SajuLimitationCode.TIME_PILLAR_UNCERTAIN, varying, limitations);
		if (luckCycle == null && candidates.stream().anyMatch(candidate -> candidate.luckCycle() != null)) {
			varying.add("luckCycle");
			limitations.add(SajuLimitationCode.LUCK_CYCLE_UNCERTAIN);
		}
		return new Resolution(
			new Candidate(new Pillars(year, month, day, time), dayMaster, elements, relations, luckCycle, annual),
			List.copyOf(limitations),
			new SajuCalculationSnapshot.Uncertainty(
				"approximate", candidates.size(), start.toString(), end.toString(), varying
			)
		);
	}

	private <T> T common(List<Candidate> candidates, Function<Candidate, T> getter) {
		T first = getter.apply(candidates.getFirst());
		return candidates.stream().allMatch(candidate -> Objects.equals(first, getter.apply(candidate)))
			? first
			: null;
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
}
