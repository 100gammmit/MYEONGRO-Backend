package com.myeongro.api.domain.saju.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class SajuInterpretationInputMapper {

	private static final Map<String, String> STEMS = Map.ofEntries(
		Map.entry("甲", "갑"), Map.entry("갑", "갑"),
		Map.entry("乙", "을"), Map.entry("을", "을"),
		Map.entry("丙", "병"), Map.entry("병", "병"),
		Map.entry("丁", "정"), Map.entry("정", "정"),
		Map.entry("戊", "무"), Map.entry("무", "무"),
		Map.entry("己", "기"), Map.entry("기", "기"),
		Map.entry("庚", "경"), Map.entry("경", "경"),
		Map.entry("辛", "신"), Map.entry("신", "신"),
		Map.entry("壬", "임"), Map.entry("임", "임"),
		Map.entry("癸", "계"), Map.entry("계", "계")
	);
	private static final Map<String, String> STEM_ELEMENTS = Map.ofEntries(
		Map.entry("갑", "목"), Map.entry("을", "목"),
		Map.entry("병", "화"), Map.entry("정", "화"),
		Map.entry("무", "토"), Map.entry("기", "토"),
		Map.entry("경", "금"), Map.entry("신", "금"),
		Map.entry("임", "수"), Map.entry("계", "수")
	);
	private static final Map<String, String> BRANCHES = Map.ofEntries(
		Map.entry("子", "자"), Map.entry("자", "자"),
		Map.entry("丑", "축"), Map.entry("축", "축"),
		Map.entry("寅", "인"), Map.entry("인", "인"),
		Map.entry("卯", "묘"), Map.entry("묘", "묘"),
		Map.entry("辰", "진"), Map.entry("진", "진"),
		Map.entry("巳", "사"), Map.entry("사", "사"),
		Map.entry("午", "오"), Map.entry("오", "오"),
		Map.entry("未", "미"), Map.entry("미", "미"),
		Map.entry("申", "신"), Map.entry("신", "신"),
		Map.entry("酉", "유"), Map.entry("유", "유"),
		Map.entry("戌", "술"), Map.entry("술", "술"),
		Map.entry("亥", "해"), Map.entry("해", "해")
	);
	private static final Map<String, String> BRANCH_ELEMENTS = Map.ofEntries(
		Map.entry("자", "수"), Map.entry("축", "토"),
		Map.entry("인", "목"), Map.entry("묘", "목"),
		Map.entry("진", "토"), Map.entry("사", "화"),
		Map.entry("오", "화"), Map.entry("미", "토"),
		Map.entry("신", "금"), Map.entry("유", "금"),
		Map.entry("술", "토"), Map.entry("해", "수")
	);
	private static final Map<String, String> ELEMENTS = Map.of(
		"wood", "목", "fire", "화", "earth", "토", "metal", "금", "water", "수"
	);
	private static final Map<String, String> TEN_GODS = Map.ofEntries(
		Map.entry("比肩", "비견"), Map.entry("비견", "비견"),
		Map.entry("劫财", "겁재"), Map.entry("劫財", "겁재"), Map.entry("겁재", "겁재"),
		Map.entry("食神", "식신"), Map.entry("식신", "식신"),
		Map.entry("伤官", "상관"), Map.entry("傷官", "상관"), Map.entry("상관", "상관"),
		Map.entry("偏财", "편재"), Map.entry("偏財", "편재"), Map.entry("편재", "편재"),
		Map.entry("正财", "정재"), Map.entry("正財", "정재"), Map.entry("정재", "정재"),
		Map.entry("七杀", "편관"), Map.entry("七殺", "편관"), Map.entry("편관", "편관"),
		Map.entry("正官", "정관"), Map.entry("정관", "정관"),
		Map.entry("偏印", "편인"), Map.entry("편인", "편인"),
		Map.entry("正印", "정인"), Map.entry("정인", "정인")
	);
	private static final Map<String, String> RELATIONS = Map.ofEntries(
		Map.entry("stem_combination", "천간의 합"),
		Map.entry("stem_clash", "천간의 충"),
		Map.entry("branch_combination", "지지의 합"), Map.entry("합", "지지의 합"),
		Map.entry("branch_clash", "지지의 충"), Map.entry("충", "지지의 충"),
		Map.entry("branch_harm", "지지의 해"), Map.entry("해", "지지의 해"),
		Map.entry("branch_break", "지지의 파"), Map.entry("파", "지지의 파"),
		Map.entry("branch_punishment", "지지의 형"), Map.entry("형", "지지의 형")
	);

	public Map<String, Object> map(Map<String, Object> snapshot, int targetYear) {
		Map<String, Object> trusted = new LinkedHashMap<>();
		Map<String, Object> rawPillars = requiredMap(snapshot.get("pillars"));
		putIfNotEmpty(trusted, "pillars", pillars(rawPillars));
		if (snapshot.get("dayMaster") instanceof String dayMaster && !dayMaster.isBlank()) {
			trusted.put("dayMaster", stemWithElement(dayMaster));
		}
		putIfNotEmpty(
			trusted, "elementBalance", elementBalance(requiredMap(snapshot.get("fiveElements")))
		);
		putIfNotEmpty(trusted, "tenGods", tenGods(rawPillars));
		putIfNotEmpty(
			trusted, "interactions", interactions(requiredList(snapshot.get("relations")))
		);
		currentLuckCycle(snapshot.get("luckCycle"), targetYear)
			.ifPresent(value -> trusted.put("currentLuckCycle", value));
		if (snapshot.get("annualFortune") instanceof Map<?, ?> annual) {
			trusted.put("annualFlow", annualFlow(castMap(annual)));
		}
		trusted.put("limitations", List.copyOf(requiredList(snapshot.get("limitations"))));
		trusted.put("uncertainty", uncertainty(requiredMap(snapshot.get("uncertainty"))));
		return immutable(trusted);
	}

	private Map<String, Object> pillars(Map<String, Object> source) {
		Map<String, Object> result = new LinkedHashMap<>();
		for (String name : List.of("year", "month", "day", "time")) {
			if (source.get(name) instanceof Map<?, ?> pillar) {
				result.put(name, pillar(castMap(pillar)));
			}
		}
		return immutable(result);
	}

	private Map<String, Object> pillar(Map<String, Object> source) {
		String stem = requiredText(source.get("stem"));
		String branch = requiredText(source.get("branch"));
		return immutable(new LinkedHashMap<>(Map.of(
			"ganZhi", ganZhi(stem, branch),
			"stem", stemWithElement(stem),
			"branch", branchWithElement(branch)
		)));
	}

	private Map<String, Object> tenGods(Map<String, Object> pillars) {
		Map<String, Object> result = new LinkedHashMap<>();
		for (String name : List.of("year", "month", "day", "time")) {
			if (!(pillars.get(name) instanceof Map<?, ?> raw)) {
				continue;
			}
			Map<String, Object> pillar = castMap(raw);
			Map<String, Object> value = new LinkedHashMap<>();
			if (pillar.get("stemTenGod") instanceof String stemTenGod
				&& !stemTenGod.isBlank()) {
				value.put("stem", tenGod(stemTenGod));
			}
			if (pillar.get("branchTenGods") instanceof List<?> branchTenGods
				&& !branchTenGods.isEmpty()) {
				value.put("branch", branchTenGods.stream()
					.map(this::requiredText)
					.map(this::tenGod)
					.toList());
			}
			if (!value.isEmpty()) {
				result.put(name, immutable(value));
			}
		}
		return immutable(result);
	}

	private Map<String, Integer> elementBalance(Map<String, Object> values) {
		Map<String, Integer> result = new LinkedHashMap<>();
		for (String name : List.of("wood", "fire", "earth", "metal", "water")) {
			Object value = values.get(name);
			if (value instanceof Number number) {
				result.put(ELEMENTS.get(name), number.intValue());
			}
		}
		return Collections.unmodifiableMap(result);
	}

	private List<Map<String, Object>> interactions(List<?> values) {
		return values.stream().map(value -> {
			Map<String, Object> relation = requiredMap(value);
			String rawType = requiredText(relation.get("type"));
			boolean stemRelation = rawType.startsWith("stem_");
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("type", RELATIONS.getOrDefault(rawType, rawType));
			result.put("members", requiredList(relation.get("members")).stream()
				.map(this::requiredText)
				.map(member -> stemRelation ? stemWithElement(member) : branchWithElement(member))
				.toList());
			return immutable(result);
		}).toList();
	}

	private java.util.Optional<Map<String, Object>> currentLuckCycle(
		Object value,
		int targetYear
	) {
		if (!(value instanceof Map<?, ?> raw)) {
			return java.util.Optional.empty();
		}
		Map<String, Object> cycle = castMap(raw);
		for (Object item : requiredList(cycle.get("periods"))) {
			Map<String, Object> period = requiredMap(item);
			int startYear = requiredInteger(period.get("startYear"));
			int endYear = requiredInteger(period.get("endYear"));
			if (targetYear < startYear || targetYear > endYear) {
				continue;
			}
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("direction", direction(requiredText(cycle.get("direction"))));
			result.put("startYear", startYear);
			result.put("endYear", endYear);
			result.put("ganZhi", ganZhi(requiredText(period.get("ganZhi"))));
			return java.util.Optional.of(immutable(result));
		}
		return java.util.Optional.empty();
	}

	private Map<String, Object> annualFlow(Map<String, Object> source) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("year", requiredInteger(source.get("year")));
		result.put("ganZhi", ganZhi(requiredText(source.get("ganZhi"))));
		if (source.get("stemTenGod") instanceof String stemTenGod && !stemTenGod.isBlank()) {
			result.put("stemTenGod", tenGod(stemTenGod));
		}
		return immutable(result);
	}

	private Map<String, Object> uncertainty(Map<String, Object> source) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("precision", requiredText(source.get("precision")));
		result.put("candidateCount", requiredInteger(source.get("candidateCount")));
		for (String key : List.of("varyingFields", "candidateZoneOffsets")) {
			if (source.get(key) instanceof List<?> values) {
				result.put(key, List.copyOf(values));
			}
		}
		return immutable(result);
	}

	private String stemWithElement(String value) {
		String stem = STEMS.getOrDefault(value, value);
		return stem + STEM_ELEMENTS.getOrDefault(stem, "");
	}

	private String branchWithElement(String value) {
		String branch = BRANCHES.getOrDefault(value, value);
		return branch + BRANCH_ELEMENTS.getOrDefault(branch, "");
	}

	private String ganZhi(String value) {
		if (value.length() < 2) {
			return value;
		}
		return ganZhi(value.substring(0, 1), value.substring(1, 2));
	}

	private String ganZhi(String stem, String branch) {
		return STEMS.getOrDefault(stem, stem) + BRANCHES.getOrDefault(branch, branch);
	}

	private String tenGod(String value) {
		return TEN_GODS.getOrDefault(value, value);
	}

	private String direction(String value) {
		return switch (value) {
			case "forward" -> "순행";
			case "backward" -> "역행";
			default -> value;
		};
	}

	private int requiredInteger(Object value) {
		if (value instanceof Number number) {
			return number.intValue();
		}
		throw new IllegalArgumentException("Saju number is required");
	}

	private String requiredText(Object value) {
		if (value instanceof String text && !text.isBlank()) {
			return text;
		}
		throw new IllegalArgumentException("Saju text is required");
	}

	private List<?> requiredList(Object value) {
		if (value instanceof List<?> list) {
			return list;
		}
		throw new IllegalArgumentException("Saju list is required");
	}

	private Map<String, Object> requiredMap(Object value) {
		if (value instanceof Map<?, ?> map
			&& map.keySet().stream().allMatch(String.class::isInstance)) {
			return castMap(map);
		}
		throw new IllegalArgumentException("Saju map is required");
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> castMap(Map<?, ?> value) {
		return (Map<String, Object>)value;
	}

	private Map<String, Object> immutable(Map<String, Object> value) {
		return Collections.unmodifiableMap(new LinkedHashMap<>(value));
	}

	private void putIfNotEmpty(Map<String, Object> target, String key, Map<?, ?> value) {
		if (!value.isEmpty()) {
			target.put(key, value);
		}
	}

	private void putIfNotEmpty(Map<String, Object> target, String key, List<?> value) {
		if (!value.isEmpty()) {
			target.put(key, value);
		}
	}
}
