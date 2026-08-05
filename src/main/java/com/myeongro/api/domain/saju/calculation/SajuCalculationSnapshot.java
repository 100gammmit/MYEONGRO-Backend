package com.myeongro.api.domain.saju.calculation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record SajuCalculationSnapshot(
	String calculationVersion,
	String engine,
	String engineVersion,
	String cityCatalogVersion,
	int targetYear,
	TimeCorrection timeCorrection,
	Pillars pillars,
	String dayMaster,
	Map<String, Integer> fiveElements,
	List<Relation> relations,
	LuckCycle luckCycle,
	AnnualFortune annualFortune,
	List<String> limitations,
	Uncertainty uncertainty
) {
	public SajuCalculationSnapshot {
		fiveElements = Map.copyOf(new LinkedHashMap<>(fiveElements));
		relations = List.copyOf(relations);
		limitations = List.copyOf(limitations);
	}

	public record TimeCorrection(
		String civilTime,
		String trueSolarTime,
		String zoneOffset,
		double longitudeCorrectionMinutes,
		double equationOfTimeMinutes
	) {
	}

	public record Pillars(Pillar year, Pillar month, Pillar day, Pillar time) {
	}

	public record Pillar(
		String ganZhi,
		String stem,
		String branch,
		String fiveElements,
		String stemTenGod,
		List<String> branchTenGods
	) {
		public Pillar {
			branchTenGods = List.copyOf(branchTenGods);
		}
	}

	public record Relation(String type, List<String> members) {
		public Relation {
			members = List.copyOf(members);
		}
	}

	public record LuckCycle(
		String direction,
		String startDate,
		int startAgeYears,
		int startAgeMonths,
		List<MajorLuckPeriod> periods
	) {
		public LuckCycle {
			periods = List.copyOf(periods);
		}
	}

	public record MajorLuckPeriod(
		int startYear,
		int endYear,
		int startAge,
		int endAge,
		String ganZhi
	) {
	}

	public record AnnualFortune(int year, String ganZhi, String stemTenGod) {
	}

	public record Uncertainty(
		String precision,
		int candidateCount,
		String rangeStart,
		String rangeEnd,
		List<String> varyingFields
	) {
		public Uncertainty {
			varyingFields = List.copyOf(varyingFields);
		}
	}
}
