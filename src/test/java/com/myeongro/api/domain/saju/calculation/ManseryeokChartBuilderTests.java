package com.myeongro.api.domain.saju.calculation;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot.*;
import com.myeongro.api.domain.saju.calculation.LunarJavaFourPillarsAdapter.Candidate;
import com.myeongro.api.domain.saju.model.LuckDirectionBasis;
import com.nlf.calendar.Solar;

class ManseryeokChartBuilderTests {
	private Pillar pillar(String branch) { return branch==null?null:new Pillar("甲"+branch,"甲",branch,"wood",null,List.of()); }
	private Candidate candidate(String branches,List<Relation> relations) {
		return new Candidate(new Pillars(pillar(branches.substring(0,1)),pillar(branches.substring(1,2)),pillar(branches.substring(2,3)),pillar(branches.substring(3,4))),"甲",Map.of(),relations,null,null);
	}
	private ManseryeokChartBuilder.Accumulator accumulate(Candidate candidate) {
		var result=new ManseryeokChartBuilder().accumulator();
		result.accept(candidate,new TrueSolarTimeCorrector().correct(LocalDateTime.of(2000,6,15,14,30),127));
		return result;
	}
	@Test void triplesSuppressOnlyIncludedHalvesAndDuplicateSymbolsRetainPositions() {
		var relations=accumulate(candidate("申子辰午",List.of())).relations();
		assertThat(relations.values()).filteredOn(r->r.type().equals("branch_triple_combination")).singleElement().satisfies(r->assertThat(r.members()).extracting(m->m.position()).containsExactly("year","month","day"));
		assertThat(relations.values()).noneMatch(r->r.type().equals("branch_half_combination"));
		var duplicate=accumulate(candidate("申子子午",List.of(new Relation("branch_clash",List.of("子","午"))))).relations().values();
		assertThat(duplicate).filteredOn(r->r.type().equals("branch_half_combination")).hasSize(2);
		assertThat(duplicate).filteredOn(r->r.type().equals("branch_clash")).hasSize(2);
		assertThat(duplicate).doesNotHaveDuplicates();
		assertThat(accumulate(candidate("申辰午午",List.of())).relations().values()).noneMatch(r->r.type().equals("branch_half_combination"));
		assertThat(accumulate(candidate("寅卯辰酉",List.of())).relations().values()).anyMatch(r->r.type().equals("branch_direction_combination"));
		assertThat(accumulate(candidate("子未辰午",List.of())).relations().values()).anyMatch(r->r.type().equals("branch_wonjin"));
	}
	@Test void changingCandidateRelationsAreLimitedNotAConfirmedEmptySet() {
		var accumulated=accumulate(candidate("申子辰午",List.of()));
		accumulated.accept(candidate("寅卯巳酉",List.of()),new TrueSolarTimeCorrector().correct(LocalDateTime.of(2000,6,15,14,30),127));
		assertThat(accumulated.relations().status()).isEqualTo("limited");
		assertThat(accumulated.relations().values()).isEmpty();
		assertThat(accumulated.hiddenStems().get("year")).isNull();
	}
	@Test void stagesHiddenStemsAndVoidsAgreeWithTheEngineAcrossSixtySyntheticDays() {
		var adapter=new LunarJavaFourPillarsAdapter();
		for(int i=0;i<60;i++) {
			var time=LocalDate.of(2000,6,1).plusDays(i).atTime(12,0);
			var candidate=adapter.calculate(time,time,true,LuckDirectionBasis.UNSPECIFIED,2026);
			var accumulated=accumulate(candidate); var engine=Solar.fromYmdHms(time.getYear(),time.getMonthValue(),time.getDayOfMonth(),12,0,0).getLunar().getEightChar(); engine.setSect(2);
			assertThat(accumulated.twelveStages().get("year").hanja()).isEqualTo(engine.getYearDiShi());
			assertThat(accumulated.twelveStages().get("month").hanja()).isEqualTo(engine.getMonthDiShi());
			assertThat(accumulated.twelveStages().get("day").hanja()).isEqualTo(engine.getDayDiShi());
			assertThat(accumulated.twelveStages().get("time").hanja()).isEqualTo(engine.getTimeDiShi());
			assertThat(accumulated.hiddenStems().get("day")).extracting(h->h.stem().hanja()).containsExactlyElementsOf(engine.getDayHideGan());
			assertThat(accumulated.voids().get("day").hanja()).isEqualTo(engine.getDayXunKong());
			assertThat(accumulated.voids().get("year").hanja()).isEqualTo(engine.getYearXunKong());
		}
	}
	@Test void integerLuckRoundsActualElapsedSecondsWithZeroAndPositiveHalvesUp() {
		assertThat(ManseryeokChartBuilder.roundedStartAge(0)).isZero();
		assertThat(ManseryeokChartBuilder.roundedStartAge(129599)).isZero();
		assertThat(ManseryeokChartBuilder.roundedStartAge(129600)).isEqualTo(1);
		assertThat(ManseryeokChartBuilder.roundedStartAge(388799)).isEqualTo(1);
		assertThat(ManseryeokChartBuilder.roundedStartAge(388800)).isEqualTo(2);
	}
}
