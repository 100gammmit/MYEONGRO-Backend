package com.myeongro.api.domain.saju.calculation;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot.AnnualFortune;
import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot.LuckCycle;
import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot.MajorLuckPeriod;
import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot.Pillar;
import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot.Pillars;
import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot.Relation;
import com.myeongro.api.domain.saju.model.LuckDirectionBasis;
import com.nlf.calendar.EightChar;
import com.nlf.calendar.Lunar;
import com.nlf.calendar.Solar;
import com.nlf.calendar.eightchar.DaYun;
import com.nlf.calendar.eightchar.Yun;
import com.nlf.calendar.util.LunarUtil;

@Component
public class LunarJavaFourPillarsAdapter {

	private static final Map<String, String> ELEMENTS = Map.ofEntries(
		Map.entry("甲", "wood"), Map.entry("乙", "wood"),
		Map.entry("寅", "wood"), Map.entry("卯", "wood"),
		Map.entry("丙", "fire"), Map.entry("丁", "fire"),
		Map.entry("巳", "fire"), Map.entry("午", "fire"),
		Map.entry("戊", "earth"), Map.entry("己", "earth"),
		Map.entry("辰", "earth"), Map.entry("戌", "earth"),
		Map.entry("丑", "earth"), Map.entry("未", "earth"),
		Map.entry("庚", "metal"), Map.entry("辛", "metal"),
		Map.entry("申", "metal"), Map.entry("酉", "metal"),
		Map.entry("壬", "water"), Map.entry("癸", "water"),
		Map.entry("亥", "water"), Map.entry("子", "water")
	);

	private static final Map<String, Set<String>> STEM_RELATIONS = Map.of(
		"stem_combination", Set.of("甲己", "乙庚", "丙辛", "丁壬", "戊癸"),
		"stem_clash", Set.of("甲庚", "乙辛", "丙壬", "丁癸")
	);
	private static final Map<String, Set<String>> BRANCH_RELATIONS = Map.of(
		"branch_combination", Set.of("子丑", "寅亥", "卯戌", "辰酉", "巳申", "午未"),
		"branch_clash", Set.of("子午", "丑未", "寅申", "卯酉", "辰戌", "巳亥"),
		"branch_harm", Set.of("子未", "丑午", "寅巳", "卯辰", "申亥", "酉戌"),
		"branch_break", Set.of("子酉", "丑辰", "寅亥", "卯午", "巳申", "未戌")
	);

	public Candidate calculate(
		LocalDateTime trueSolarTime,
		boolean includeTime,
		LuckDirectionBasis luckDirectionBasis,
		int targetYear
	) {
		Solar solar = Solar.fromYmdHms(
			trueSolarTime.getYear(), trueSolarTime.getMonthValue(), trueSolarTime.getDayOfMonth(),
			trueSolarTime.getHour(), trueSolarTime.getMinute(), trueSolarTime.getSecond()
		);
		Lunar lunar = solar.getLunar();
		EightChar eightChar = lunar.getEightChar();
		eightChar.setSect(SajuCalculationRules.DAY_BOUNDARY_SECT);

		Pillar year = pillar(
			eightChar.getYear(), eightChar.getYearWuXing(), eightChar.getYearShiShenGan(),
			eightChar.getYearShiShenZhi()
		);
		Pillar month = pillar(
			eightChar.getMonth(), eightChar.getMonthWuXing(), eightChar.getMonthShiShenGan(),
			eightChar.getMonthShiShenZhi()
		);
		Pillar day = pillar(
			eightChar.getDay(), eightChar.getDayWuXing(), eightChar.getDayShiShenGan(),
			eightChar.getDayShiShenZhi()
		);
		Pillar time = includeTime ? pillar(
			eightChar.getTime(), eightChar.getTimeWuXing(), eightChar.getTimeShiShenGan(),
			eightChar.getTimeShiShenZhi()
		) : null;
		Pillars pillars = new Pillars(year, month, day, time);
		String dayMaster = eightChar.getDayGan();
		LuckCycle luckCycle = includeTime
			? luckCycle(eightChar, luckDirectionBasis)
			: null;
		AnnualFortune annual = annualFortune(dayMaster, targetYear);
		return new Candidate(
			pillars,
			dayMaster,
			countElements(pillars),
			relations(pillars),
			luckCycle,
			annual
		);
	}

	private Pillar pillar(
		String ganZhi,
		String fiveElements,
		String stemTenGod,
		List<String> branchTenGods
	) {
		return new Pillar(
			ganZhi,
			ganZhi.substring(0, 1),
			ganZhi.substring(1, 2),
			fiveElements,
			stemTenGod,
			branchTenGods
		);
	}

	private LuckCycle luckCycle(EightChar eightChar, LuckDirectionBasis basis) {
		if (basis == LuckDirectionBasis.UNSPECIFIED) {
			return null;
		}
		Yun yun = eightChar.getYun(
			basis == LuckDirectionBasis.MALE ? 1 : 0,
			SajuCalculationRules.LUCK_START_SECT
		);
		List<MajorLuckPeriod> periods = new ArrayList<>();
		for (DaYun period : yun.getDaYun(9)) {
			if (period.getGanZhi() != null && !period.getGanZhi().isBlank()) {
				periods.add(new MajorLuckPeriod(
					period.getStartYear(), period.getEndYear(),
					period.getStartAge(), period.getEndAge(), period.getGanZhi()
				));
			}
		}
		return new LuckCycle(
			yun.isForward() ? "forward" : "backward",
			yun.getStartSolar().toYmdHms(),
			yun.getStartYear(),
			yun.getStartMonth(),
			periods
		);
	}

	private AnnualFortune annualFortune(String dayMaster, int targetYear) {
		String ganZhi = Solar.fromYmd(targetYear, 7, 1)
			.getLunar().getYearInGanZhiExact();
		String tenGod = (String)LunarUtil.SHI_SHEN.get(dayMaster + ganZhi.substring(0, 1));
		return new AnnualFortune(targetYear, ganZhi, tenGod);
	}

	Map<String, Integer> countElements(Pillars pillars) {
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (String element : List.of("wood", "fire", "earth", "metal", "water")) {
			counts.put(element, 0);
		}
		for (Pillar pillar : List.of(pillars.year(), pillars.month(), pillars.day())) {
			increment(counts, pillar.stem());
			increment(counts, pillar.branch());
		}
		if (pillars.time() != null) {
			increment(counts, pillars.time().stem());
			increment(counts, pillars.time().branch());
		}
		return counts;
	}

	private void increment(Map<String, Integer> counts, String symbol) {
		String element = ELEMENTS.get(symbol);
		counts.computeIfPresent(element, (ignored, value) -> value + 1);
	}

	List<Relation> relations(Pillars pillars) {
		List<Pillar> values = new ArrayList<>(List.of(
			pillars.year(), pillars.month(), pillars.day()
		));
		if (pillars.time() != null) {
			values.add(pillars.time());
		}
		List<Relation> relations = new ArrayList<>();
		for (int left = 0; left < values.size(); left++) {
			for (int right = left + 1; right < values.size(); right++) {
				addPairRelations(relations, values.get(left).stem(), values.get(right).stem(), STEM_RELATIONS);
				addPairRelations(relations, values.get(left).branch(), values.get(right).branch(), BRANCH_RELATIONS);
			}
		}
		addBranchPunishments(relations, values.stream().map(Pillar::branch).toList());
		return List.copyOf(new LinkedHashSet<>(relations));
	}

	private void addPairRelations(
		List<Relation> relations,
		String left,
		String right,
		Map<String, Set<String>> rules
	) {
		for (Map.Entry<String, Set<String>> rule : rules.entrySet()) {
			if (rule.getValue().contains(left + right) || rule.getValue().contains(right + left)) {
				relations.add(new Relation(rule.getKey(), List.of(left, right)));
			}
		}
	}

	private void addBranchPunishments(List<Relation> relations, List<String> branches) {
		for (String self : List.of("辰", "午", "酉", "亥")) {
			if (branches.stream().filter(self::equals).count() >= 2) {
				relations.add(new Relation("branch_punishment", List.of(self, self)));
			}
		}
		addPunishmentSet(relations, branches, List.of("寅", "巳", "申"));
		addPunishmentSet(relations, branches, List.of("丑", "戌", "未"));
		if (branches.contains("子") && branches.contains("卯")) {
			relations.add(new Relation("branch_punishment", List.of("子", "卯")));
		}
	}

	private void addPunishmentSet(
		List<Relation> relations,
		List<String> branches,
		List<String> required
	) {
		if (branches.containsAll(required)) {
			relations.add(new Relation("branch_punishment", required));
		}
	}

	public record Candidate(
		Pillars pillars,
		String dayMaster,
		Map<String, Integer> fiveElements,
		List<Relation> relations,
		LuckCycle luckCycle,
		AnnualFortune annualFortune
	) {
		public Candidate {
			fiveElements = Map.copyOf(fiveElements);
			relations = List.copyOf(relations);
		}
	}
}
