package com.myeongro.api.domain.saju.calculation;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Component;
import com.nlf.calendar.Solar;
import com.nlf.calendar.EightChar;
import com.nlf.calendar.util.LunarUtil;
import com.myeongro.api.domain.saju.calculation.ManseryeokChart.*;
import com.myeongro.api.domain.saju.calculation.LunarJavaFourPillarsAdapter.Candidate;
import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot.Pillar;

@Component
public class ManseryeokChartBuilder {
	private static final List<String> POSITIONS = List.of("year", "month", "day", "time");
	private static final String STEMS = "甲乙丙丁戊己庚辛壬癸", BRANCHES = "子丑寅卯辰巳午未申酉戌亥";
	// lunar-java EightChar.getDiShi offsets; apply to the independently selected engine/solar pillars.
	private static final int[] STAGE_OFFSETS = {1,6,10,9,10,9,7,0,4,3};
	private static final List<String> TRIPLES = List.of("申子辰", "亥卯未", "寅午戌", "巳酉丑");
	private static final List<String> HALVES = List.of("申子", "子辰", "亥卯", "卯未", "寅午", "午戌", "巳酉", "酉丑");
	private static final List<String> DIRECTIONS = List.of("寅卯辰", "巳午未", "申酉戌", "亥子丑");
	private static final List<String> WONJIN = List.of("子未", "丑午", "寅酉", "卯申", "辰亥", "巳戌");
	private static final List<String> RELATION_ORDER = List.of("stem_combination", "stem_clash", "branch_combination", "branch_clash", "branch_harm", "branch_break", "branch_punishment", "branch_triple_combination", "branch_half_combination", "branch_direction_combination", "branch_wonjin");
	public Accumulator accumulator() { return new Accumulator(); }
	public static Symbol stem(String value) { return value == null ? null : new Symbol(value, SajuKoreanTerms.stem(value)); }
	public static Symbol branch(String value) { return value == null ? null : new Symbol(value, SajuKoreanTerms.branch(value)); }
	public static Symbol ganZhi(String value) { return value == null ? null : new Symbol(value, SajuKoreanTerms.ganZhi(value)); }

	public Chart chart(Accumulator accumulated) {
		var pillars = new LinkedHashMap<String,ChartPillar>();
		for (String id : POSITIONS) {
			Pillar pillar = accumulated.commonPillars.get(id);
			pillars.put(id, pillar == null ? null : new ChartPillar(ganZhi(pillar.ganZhi()), stem(pillar.stem()), branch(pillar.branch()), accumulated.dayMaster == null ? null : SajuKoreanTerms.tenGod(pillar.stemTenGod())));
		}
		return new Chart(Collections.unmodifiableMap(pillars), stem(accumulated.dayMaster));
	}
	public List<AnnualFlow> annualFlows(int referenceYear, String dayMaster) {
		var result = new ArrayList<AnnualFlow>();
		for (int year = referenceYear; year < referenceYear + 10; year++) {
			String value = Solar.fromYmd(year, 7, 1).getLunar().getYearInGanZhiExact();
			result.add(new AnnualFlow(year, ganZhi(value), dayMaster == null ? null : SajuKoreanTerms.tenGod(LunarUtil.SHI_SHEN.get(dayMaster + value.substring(0,1)))));
		}
		return List.copyOf(result);
	}
	public Map<String,Capability> capabilities(SajuCalculationSnapshot snapshot, Accumulator a) {
		var result = new LinkedHashMap<String,Capability>();
		boolean personal = a.dayMaster != null;
		boolean completePersonal = personal && a.commonPillars.values().stream().allMatch(p->p!=null && p.stemTenGod()!=null);
		result.put("natal_personal", capability(!personal ? "unavailable" : completePersonal ? "available" : "limited",
			List.of("chart.dayMaster", "chart.pillars.*.stemTenGod"), !personal ? "DAY_MASTER_UNCERTAIN" : completePersonal ? null : "NATAL_PILLARS_PARTIALLY_UNCERTAIN"));
		result.put("time_pillar", capability(snapshot.pillars().time() != null ? "available" : "unavailable", List.of("chart.pillars.time"), snapshot.pillars().time() != null ? null : "TIME_PILLAR_UNCERTAIN"));
		result.put("major_luck", capability(snapshot.luckCycle() != null && a.integerLuck != null ? "available" : "unavailable", List.of("luckCycle"), snapshot.luckCycle() != null && a.integerLuck != null ? null : "MAJOR_LUCK_UNAVAILABLE"));
		result.put("annual_personal", capability(personal ? "available" : "limited", List.of("annualFlows"), personal ? null : "DAY_MASTER_UNCERTAIN"));
		result.put("monthly_personal", capability(personal ? "available" : "limited", List.of("monthlyCalendar"), personal ? null : "DAY_MASTER_UNCERTAIN"));
		return Collections.unmodifiableMap(result);
	}
	private Capability capability(String status, List<String> fields, String reason) { return new Capability(status, fields, reason == null ? List.of() : List.of(reason)); }

	public static final class Accumulator implements SajuCandidateObserver {
		private int count;
		private final Map<String,Pillar> commonPillars = new LinkedHashMap<>();
		private final Map<String,List<HiddenStem>> hidden = new LinkedHashMap<>();
		private final Map<String,Symbol> stages = new LinkedHashMap<>(), voids = new LinkedHashMap<>();
		private final Set<PositionedRelation> relations = new LinkedHashSet<>();
		private final Set<String> varying = new LinkedHashSet<>();
		private boolean relationVariation;
		private String dayMaster;
		private Integer integerLuck;
		@Override public void accept(Candidate c, TrueSolarTimeCorrector.Correction correction) {
			var pillars = Arrays.asList(c.pillars().year(), c.pillars().month(), c.pillars().day(), c.pillars().time());
			var candidateRelations = positioned(c, pillars);
			for (int i=0; i<4; i++) {
				String id = POSITIONS.get(i); Pillar p = pillars.get(i);
				List<HiddenStem> hs = p == null ? null : LunarUtil.ZHI_HIDE_GAN.get(p.branch()).stream()
					.map(value -> new HiddenStem(stem(value), SajuKoreanTerms.tenGod(LunarUtil.SHI_SHEN.get(c.dayMaster() + value)))).toList();
				Symbol stage = p == null ? null : stage(c.dayMaster(), p.branch());
				if (count == 0) { commonPillars.put(id,p); hidden.put(id,hs); stages.put(id,stage); }
				else {
					Pillar old = commonPillars.get(id);
					if (old != null && !old.ganZhi().equals(p == null ? null : p.ganZhi())) commonPillars.put(id,null);
					else if (old != null && !Objects.equals(old.stemTenGod(),p.stemTenGod())) commonPillars.put(id,new Pillar(old.ganZhi(),old.stem(),old.branch(),old.fiveElements(),null,List.of()));
					mergeHidden(id, hs);
					merge(stages,id,stage,"twelveStages."+id);
				}
			}
			Symbol dayVoid = ManseryeokChartBuilder.voids(c.pillars().day()), yearVoid = ManseryeokChartBuilder.voids(c.pillars().year());
			Integer roundedLuck = ManseryeokChartBuilder.integerLuck(c, correction);
			if (count == 0) {
				dayMaster = c.dayMaster(); integerLuck = roundedLuck;
				voids.put("day",dayVoid); voids.put("year",yearVoid); relations.addAll(candidateRelations);
			} else {
				if (!Objects.equals(dayMaster,c.dayMaster())) dayMaster = null;
				if (!Objects.equals(integerLuck,roundedLuck)) { integerLuck = null; varying.add("luckCycle.integerStartAge"); }
				merge(voids,"day",dayVoid,"voids.day"); merge(voids,"year",yearVoid,"voids.year");
				relationVariation |= !relations.equals(candidateRelations); relations.retainAll(candidateRelations);
			}
			count++;
		}
		private void mergeHidden(String id, List<HiddenStem> next) {
			List<HiddenStem> old = hidden.get(id);
			if (old == null || next == null || !old.stream().map(HiddenStem::stem).toList().equals(next.stream().map(HiddenStem::stem).toList())) {
				if (!Objects.equals(old,next)) varying.add("hiddenStems."+id);
				hidden.put(id,null); return;
			}
			var merged = new ArrayList<HiddenStem>();
			for (int i=0;i<old.size();i++) merged.add(new HiddenStem(old.get(i).stem(),Objects.equals(old.get(i).tenGod(),next.get(i).tenGod()) ? old.get(i).tenGod() : null));
			if (!merged.equals(old)) varying.add("hiddenStems."+id+".tenGod");
			hidden.put(id,List.copyOf(merged));
		}
		private <T> void merge(Map<String,T> values, String key, T next, String field) {
			if (!Objects.equals(values.get(key),next)) { values.put(key,null); varying.add(field); }
		}
		public Map<String,List<HiddenStem>> hiddenStems() {
			var result = new LinkedHashMap<String,List<HiddenStem>>();
			hidden.forEach((key,value)->result.put(key,value == null ? null : value.stream().map(h->new HiddenStem(h.stem(),dayMaster==null ? null : h.tenGod())).toList()));
			return Collections.unmodifiableMap(result);
		}
		public Map<String,Symbol> twelveStages() {
			var result = new LinkedHashMap<>(stages);
			if (dayMaster == null) result.replaceAll((key,value)->null);
			return Collections.unmodifiableMap(result);
		}
		public Map<String,Symbol> voids() { return Collections.unmodifiableMap(voids); }
		public String dayMaster() { return dayMaster; }
		public Integer integerLuck() { return integerLuck; }
		public Set<String> varyingFields() { return Collections.unmodifiableSet(varying); }
		public CollectionValue<PositionedRelation> relations() {
			var sorted = relations.stream().sorted(Comparator.comparingInt((PositionedRelation r)->RELATION_ORDER.indexOf(r.type())).thenComparing(r->r.members().stream().map(m->Integer.toString(POSITIONS.indexOf(m.position()))).reduce("",String::concat))).toList();
			return new CollectionValue<>(relationVariation ? "limited" : "available",sorted,relationVariation ? List.of("RELATIONS_VARY_ACROSS_CANDIDATES") : List.of());
		}
	}
	private static Symbol stage(String stem, String branch) {
		int index = STEMS.indexOf(stem);
		String value = EightChar.CHANG_SHENG[Math.floorMod(STAGE_OFFSETS[index] + (index%2==0 ? BRANCHES.indexOf(branch) : -BRANCHES.indexOf(branch)),12)];
		return new Symbol(value,SajuKoreanTerms.twelveStage(value));
	}
	private static Symbol voids(Pillar pillar) {
		if (pillar == null) return null;
		String value = LunarUtil.getXunKong(pillar.ganZhi());
		return new Symbol(value,SajuKoreanTerms.branch(value.substring(0,1))+SajuKoreanTerms.branch(value.substring(1,2)));
	}
	private static Integer integerLuck(Candidate c, TrueSolarTimeCorrector.Correction correction) {
		if (c.luckCycle() == null) return null;
		LocalDateTime civil = correction.engineCivilTime();
		var lunar = Solar.fromYmdHms(civil.getYear(),civil.getMonthValue(),civil.getDayOfMonth(),civil.getHour(),civil.getMinute(),civil.getSecond()).getLunar();
		var term = c.luckCycle().direction().equals("forward") ? lunar.getNextJie().getSolar() : lunar.getPrevJie().getSolar();
		LocalDateTime boundary = LocalDateTime.parse(term.toYmdHms().replace(' ','T'));
		long seconds = Math.abs(Duration.between(civil,boundary).getSeconds());
		return roundedStartAge(seconds);
	}
	static int roundedStartAge(long seconds) { return (int)Math.round(seconds/(3d*86400d)); }
	private static Set<PositionedRelation> positioned(Candidate c, List<Pillar> pillars) {
		var result = new LinkedHashSet<PositionedRelation>();
		for (var r : c.relations()) {
			boolean stem = r.type().startsWith("stem_");
			for (List<Integer> indices : subsets(pillars,r.members().size())) {
				List<String> symbols = indices.stream().map(i->stem ? pillars.get(i).stem() : pillars.get(i).branch()).sorted().toList();
				if (symbols.equals(r.members().stream().sorted().toList())) result.add(relation(r.type(),indices,pillars,stem));
			}
		}
		addRelations(result,"branch_triple_combination",TRIPLES,pillars);
		addRelations(result,"branch_half_combination",HALVES,pillars);
		addRelations(result,"branch_direction_combination",DIRECTIONS,pillars);
		addRelations(result,"branch_wonjin",WONJIN,pillars);
		var full = result.stream().filter(r->r.type().equals("branch_triple_combination")).toList();
		result.removeIf(r->r.type().equals("branch_half_combination") && full.stream().anyMatch(t->t.members().containsAll(r.members())));
		return result;
	}
	private static void addRelations(Set<PositionedRelation> result, String type, List<String> rules, List<Pillar> pillars) {
		for (String rule : rules) for (List<Integer> indices : subsets(pillars,rule.length())) {
			String actual = indices.stream().map(i->pillars.get(i).branch()).sorted().reduce("",String::concat);
			if (actual.equals(rule.chars().mapToObj(ch->Character.toString((char)ch)).sorted().reduce("",String::concat))) result.add(relation(type,indices,pillars,false));
		}
	}
	private static PositionedRelation relation(String type, List<Integer> indices, List<Pillar> pillars, boolean isStem) {
		return new PositionedRelation(type,indices.stream().map(i->new Member(POSITIONS.get(i),isStem ? stem(pillars.get(i).stem()) : branch(pillars.get(i).branch()))).toList());
	}
	private static List<List<Integer>> subsets(List<Pillar> pillars, int size) {
		var result = new ArrayList<List<Integer>>();
		for (int mask=1;mask<16;mask++) if (Integer.bitCount(mask)==size) {
			var selected = new ArrayList<Integer>();
			for (int i=0;i<4;i++) if ((mask&(1<<i))!=0 && pillars.get(i)!=null) selected.add(i);
			if (selected.size()==size) result.add(List.copyOf(selected));
		}
		return result;
	}
}
