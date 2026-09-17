package com.myeongro.api.domain.saju.calculation;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot.AnnualFortune;
import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot.LuckCycle;
import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot.MajorLuckPeriod;
import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot.Pillar;
import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot.Pillars;
import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot.Relation;

/**
 * lunar-java는 간지를 한자로, 십성을 중국어 간체로 돌려준다. 계산 규칙은 한자 그대로 판정하고,
 * 저장·표시·해석 입력에는 이 표로 바꾼 한국어 명리 용어를 쓴다. 이미 한글인 값은 그대로 둔다.
 */
public final class SajuKoreanTerms {

	private static final Map<String, String> STEMS = Map.of(
		"甲", "갑", "乙", "을", "丙", "병", "丁", "정", "戊", "무",
		"己", "기", "庚", "경", "辛", "신", "壬", "임", "癸", "계"
	);
	private static final Map<String, String> BRANCHES = Map.ofEntries(
		Map.entry("子", "자"), Map.entry("丑", "축"), Map.entry("寅", "인"),
		Map.entry("卯", "묘"), Map.entry("辰", "진"), Map.entry("巳", "사"),
		Map.entry("午", "오"), Map.entry("未", "미"), Map.entry("申", "신"),
		Map.entry("酉", "유"), Map.entry("戌", "술"), Map.entry("亥", "해")
	);
	private static final Map<String, String> ELEMENTS = Map.of(
		"木", "목", "火", "화", "土", "토", "金", "금", "水", "수"
	);
	private static final Map<String, String> TEN_GODS = Map.ofEntries(
		Map.entry("比肩", "비견"),
		Map.entry("劫财", "겁재"), Map.entry("劫財", "겁재"),
		Map.entry("食神", "식신"),
		Map.entry("伤官", "상관"), Map.entry("傷官", "상관"),
		Map.entry("偏财", "편재"), Map.entry("偏財", "편재"),
		Map.entry("正财", "정재"), Map.entry("正財", "정재"),
		Map.entry("七杀", "편관"), Map.entry("七殺", "편관"),
		Map.entry("正官", "정관"),
		Map.entry("偏印", "편인"),
		Map.entry("正印", "정인")
	);

	private SajuKoreanTerms() {
	}

	public static String stem(String value) {
		return value == null ? null : STEMS.getOrDefault(value, value);
	}

	public static String branch(String value) {
		return value == null ? null : BRANCHES.getOrDefault(value, value);
	}

	public static String tenGod(String value) {
		return value == null ? null : TEN_GODS.getOrDefault(value, value);
	}

	public static String ganZhi(String value) {
		if (value == null || value.length() != 2) {
			return value;
		}
		return stem(value.substring(0, 1)) + branch(value.substring(1, 2));
	}

	static Pillars pillars(Pillars pillars) {
		return new Pillars(
			pillar(pillars.year()), pillar(pillars.month()),
			pillar(pillars.day()), pillar(pillars.time())
		);
	}

	static List<Relation> relations(List<Relation> relations) {
		return relations.stream()
			.map(relation -> new Relation(
				relation.type(),
				relation.members().stream()
					.map(member -> relation.type().startsWith("stem_") ? stem(member) : branch(member))
					.toList()
			))
			.toList();
	}

	static LuckCycle luckCycle(LuckCycle cycle) {
		if (cycle == null) {
			return null;
		}
		return new LuckCycle(
			cycle.direction(), cycle.startDate(), cycle.startAgeYears(), cycle.startAgeMonths(),
			cycle.periods().stream()
				.map(period -> new MajorLuckPeriod(
					period.startYear(), period.endYear(), period.startAge(), period.endAge(),
					ganZhi(period.ganZhi())
				))
				.toList()
		);
	}

	static AnnualFortune annualFortune(AnnualFortune annual) {
		if (annual == null) {
			return null;
		}
		return new AnnualFortune(annual.year(), ganZhi(annual.ganZhi()), tenGod(annual.stemTenGod()));
	}

	private static Pillar pillar(Pillar pillar) {
		if (pillar == null) {
			return null;
		}
		return new Pillar(
			ganZhi(pillar.ganZhi()),
			stem(pillar.stem()),
			branch(pillar.branch()),
			elements(pillar.fiveElements()),
			tenGod(pillar.stemTenGod()),
			pillar.branchTenGods().stream().map(SajuKoreanTerms::tenGod).toList()
		);
	}

	private static String elements(String value) {
		if (value == null) {
			return null;
		}
		return value.codePoints()
			.mapToObj(Character::toString)
			.map(symbol -> ELEMENTS.getOrDefault(symbol, symbol))
			.collect(Collectors.joining());
	}
}
