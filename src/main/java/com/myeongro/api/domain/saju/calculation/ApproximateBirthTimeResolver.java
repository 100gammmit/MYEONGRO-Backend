package com.myeongro.api.domain.saju.calculation;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
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
	private final boolean segmentedUnknown;

	@org.springframework.beans.factory.annotation.Autowired
	public ApproximateBirthTimeResolver(
		TrueSolarTimeCorrector corrector,
		LunarJavaFourPillarsAdapter adapter
	) {
		this(corrector, adapter, true);
	}

	/**
	 * {@code segmentedUnknown=false} keeps the original minute-by-minute search for every range.
	 * Tests use it as the exhaustive reference that the segmented unknown-time search must equal.
	 */
	ApproximateBirthTimeResolver(
		TrueSolarTimeCorrector corrector,
		LunarJavaFourPillarsAdapter adapter,
		boolean segmentedUnknown
	) {
		this.corrector = corrector;
		this.adapter = adapter;
		this.segmentedUnknown = segmentedUnknown;
	}

	public Resolution resolve(
		LocalDateTime rememberedTime,
		double longitude,
		LuckDirectionBasis luckDirectionBasis,
		int targetYear,
		Runnable checkDeadline
	) {
		return resolve(rememberedTime, longitude, luckDirectionBasis, targetYear, checkDeadline,
			SajuCalculationSettings.LEGACY, SajuCandidateObserver.NONE);
	}

	public Resolution resolve(LocalDateTime rememberedTime, double longitude, LuckDirectionBasis luckDirectionBasis,
		int targetYear, Runnable checkDeadline, SajuCalculationSettings settings, SajuCandidateObserver observer) {
		LocalDateTime start = rememberedTime.minusMinutes(SajuCalculationRules.APPROXIMATE_MINUTES);
		LocalDateTime end = rememberedTime.plusMinutes(SajuCalculationRules.APPROXIMATE_MINUTES);
		CandidateSet candidateSet = candidatesForRange(
			start, end, longitude, true, luckDirectionBasis, targetYear, checkDeadline, settings, observer
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
		int targetYear,
		Runnable checkDeadline
	) {
		return resolveExact(corrections, luckDirectionBasis, targetYear, checkDeadline,
			SajuCalculationSettings.LEGACY, SajuCandidateObserver.NONE);
	}

	public Resolution resolveExact(List<TrueSolarTimeCorrector.Correction> corrections, LuckDirectionBasis luckDirectionBasis,
		int targetYear, Runnable checkDeadline, SajuCalculationSettings settings, SajuCandidateObserver observer) {
		if (corrections.isEmpty()) {
			throw new IllegalArgumentException("Civil time does not exist");
		}
		List<Candidate> candidates = corrections.stream()
			.map(correction -> {
				checkDeadline.run();
				return calculate(correction, true, luckDirectionBasis, targetYear, settings, observer);
			})
			.toList();
		CandidateSet candidateSet = new CandidateSet(
			candidates,
			candidates.size(),
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
		int targetYear,
		Runnable checkDeadline
	) {
		return resolveUnknown(birthDate, longitude, luckDirectionBasis, targetYear, checkDeadline,
			SajuCalculationSettings.LEGACY, SajuCandidateObserver.NONE);
	}

	public Resolution resolveUnknown(LocalDate birthDate, double longitude, LuckDirectionBasis luckDirectionBasis,
		int targetYear, Runnable checkDeadline, SajuCalculationSettings settings, SajuCandidateObserver observer) {
		LocalDateTime start = birthDate.atStartOfDay();
		LocalDateTime end = birthDate.atTime(23, 59);
		CandidateSet candidateSet = candidatesForRange(
			start, end, longitude, false, luckDirectionBasis, targetYear, checkDeadline, settings, observer
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
		Map<String, Integer> elements = LunarJavaFourPillarsAdapter.countElements(
			new Pillars(year, month, day, time)
		);
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
				precision, candidateSet.candidateCount(), start.toString(), end.toString(), varying,
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
		int targetYear,
		Runnable checkDeadline, SajuCalculationSettings settings, SajuCandidateObserver observer
	) {
		if (!includeTime && segmentedUnknown) {
			return segmentedCandidates(start, end, longitude, luckDirectionBasis, targetYear, checkDeadline, settings, observer);
		}
		List<Candidate> candidates = new ArrayList<>();
		Set<String> offsets = new LinkedHashSet<>();
		boolean gap = false;
		boolean overlap = false;
		for (LocalDateTime cursor = start; !cursor.isAfter(end); cursor = cursor.plusMinutes(1)) {
			checkDeadline.run();
			List<TrueSolarTimeCorrector.Correction> corrections = corrections(cursor, longitude, settings);
			gap |= corrections.isEmpty();
			overlap |= corrections.size() > 1;
			for (TrueSolarTimeCorrector.Correction correction : corrections) {
				checkDeadline.run();
				offsets.add(correction.zoneOffset());
				candidates.add(calculate(correction, includeTime, luckDirectionBasis, targetYear, settings, observer));
			}
		}
		checkDeadline.run();
		return new CandidateSet(candidates, candidates.size(), offsets, gap, overlap);
	}

	/**
	 * Unknown-time search without the per-minute engine call. Without the time pillar and luck cycle a candidate is a
	 * function of the year, month and day pillars only, and each of them is a step function of time that does not
	 * return to an earlier value within one civil day. So inside one run of minutes that share a zone offset, two equal
	 * end candidates mean every minute between them is equal, and bisection finds the few change points.
	 * Merging and the observer keep only values shared by all candidates, so feeding one representative per run in the
	 * original minute/offset order yields the same result as feeding every minute. Counts, offsets and DST flags still
	 * come from every minute through the cheap correction step.
	 */
	private CandidateSet segmentedCandidates(LocalDateTime start, LocalDateTime end, double longitude,
		LuckDirectionBasis basis, int targetYear, Runnable checkDeadline, SajuCalculationSettings settings,
		SajuCandidateObserver observer) {
		Set<String> offsets = new LinkedHashSet<>();
		boolean gap = false;
		boolean overlap = false;
		int candidateCount = 0;
		List<Branch> branches = new ArrayList<>();
		Map<String, Branch> open = new java.util.HashMap<>();
		int minute = 0;
		for (LocalDateTime cursor = start; !cursor.isAfter(end); cursor = cursor.plusMinutes(1), minute++) {
			checkDeadline.run();
			List<TrueSolarTimeCorrector.Correction> corrections = corrections(cursor, longitude, settings);
			gap |= corrections.isEmpty();
			overlap |= corrections.size() > 1;
			candidateCount += corrections.size();
			for (int order = 0; order < corrections.size(); order++) {
				TrueSolarTimeCorrector.Correction correction = corrections.get(order);
				offsets.add(correction.zoneOffset());
				Branch branch = open.get(correction.zoneOffset());
				if (branch == null || branch.lastMinute() != minute - 1) {
					branch = new Branch(new ArrayList<>());
					branches.add(branch);
					open.put(correction.zoneOffset(), branch);
				}
				branch.points().add(new Point(minute, order, correction));
			}
		}
		List<Run> runs = new ArrayList<>();
		for (Branch branch : branches) {
			Map<Integer, Candidate> memo = new java.util.HashMap<>();
			List<Run> branchRuns = new ArrayList<>();
			collectRuns(branch.points(), 0, branch.points().size() - 1, memo, branchRuns,
				point -> {
					checkDeadline.run();
					return compute(point.correction(), false, basis, targetYear, settings);
				});
			runs.addAll(branchRuns);
		}
		runs.sort(java.util.Comparator.comparingInt((Run run) -> run.point().minute())
			.thenComparingInt(run -> run.point().order()));
		List<Candidate> candidates = new ArrayList<>(runs.size());
		for (Run run : runs) {
			observer.accept(run.candidate(), run.point().correction());
			candidates.add(run.candidate());
		}
		checkDeadline.run();
		return new CandidateSet(candidates, candidateCount, offsets, gap, overlap);
	}

	private void collectRuns(List<Point> points, int low, int high, Map<Integer, Candidate> memo, List<Run> runs,
		Function<Point, Candidate> engine) {
		Candidate first = memo.computeIfAbsent(low, index -> engine.apply(points.get(index)));
		Candidate last = memo.computeIfAbsent(high, index -> engine.apply(points.get(index)));
		if (first.equals(last)) {
			appendRun(runs, points.get(low), first);
			return;
		}
		if (high - low == 1) {
			appendRun(runs, points.get(low), first);
			appendRun(runs, points.get(high), last);
			return;
		}
		int middle = (low + high) >>> 1;
		collectRuns(points, low, middle, memo, runs, engine);
		collectRuns(points, middle, high, memo, runs, engine);
	}

	private void appendRun(List<Run> runs, Point point, Candidate candidate) {
		if (runs.isEmpty() || !runs.getLast().candidate().equals(candidate)) {
			runs.add(new Run(point, candidate));
		}
	}

	private List<TrueSolarTimeCorrector.Correction> corrections(LocalDateTime cursor, double longitude,
		SajuCalculationSettings settings) {
		return settings == SajuCalculationSettings.LEGACY ? corrector.correctCandidates(cursor, longitude)
			: corrector.correctCandidates(cursor, longitude, settings);
	}

	private Candidate calculate(TrueSolarTimeCorrector.Correction correction, boolean includeTime,
		LuckDirectionBasis basis, int year, SajuCalculationSettings settings, SajuCandidateObserver observer) {
		Candidate candidate = compute(correction, includeTime, basis, year, settings);
		observer.accept(candidate, correction);
		return candidate;
	}

	private Candidate compute(TrueSolarTimeCorrector.Correction correction, boolean includeTime,
		LuckDirectionBasis basis, int year, SajuCalculationSettings settings) {
		return settings == SajuCalculationSettings.LEGACY
			? adapter.calculate(correction.trueSolarTime(), correction.engineCivilTime(), includeTime, basis, year)
			: adapter.calculate(correction.trueSolarTime(), correction.engineCivilTime(), includeTime, basis, year, settings.options());
	}

	private record Point(int minute, int order, TrueSolarTimeCorrector.Correction correction) {
	}

	private record Branch(List<Point> points) {
		int lastMinute() {
			return points.getLast().minute();
		}
	}

	private record Run(Point point, Candidate candidate) {
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

	/** {@code candidateCount} is the number of minute/offset candidates searched, even when only distinct runs are kept. */
	private record CandidateSet(
		List<Candidate> candidates,
		int candidateCount,
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
