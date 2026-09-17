package com.myeongro.api.domain.saju.calculation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot.AnnualFortune;
import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot.LuckCycle;
import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot.MajorLuckPeriod;
import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot.Pillar;
import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot.Pillars;
import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot.Relation;

class SajuKoreanTermsTests {

	@Test
	void translatesEngineTermsIntoKoreanSajuTerms() {
		Pillars pillars = SajuKoreanTerms.pillars(new Pillars(
			new Pillar("壬申", "壬", "申", "水金", "正印", List.of("正官", "偏印")),
			null,
			new Pillar("乙丑", "乙", "丑", "木土", "比肩", List.of("偏财", "七杀")),
			null
		));

		assertThat(pillars.year()).isEqualTo(
			new Pillar("임신", "임", "신", "수금", "정인", List.of("정관", "편인"))
		);
		assertThat(pillars.month()).isNull();
		assertThat(pillars.day().branchTenGods()).containsExactly("편재", "편관");
		assertThat(SajuKoreanTerms.annualFortune(new AnnualFortune(2026, "丙午", "伤官")))
			.isEqualTo(new AnnualFortune(2026, "병오", "상관"));
		assertThat(SajuKoreanTerms.annualFortune(new AnnualFortune(2026, "丙午", null)))
			.isEqualTo(new AnnualFortune(2026, "병오", null));
	}

	@Test
	void keepsRelationCodesAndLuckDirectionWhileTranslatingTheirCharacters() {
		assertThat(SajuKoreanTerms.relations(List.of(
			new Relation("stem_combination", List.of("戊", "癸")),
			new Relation("branch_punishment", List.of("丑", "戌", "未"))
		))).containsExactly(
			new Relation("stem_combination", List.of("무", "계")),
			new Relation("branch_punishment", List.of("축", "술", "미"))
		);
		LuckCycle cycle = SajuKoreanTerms.luckCycle(new LuckCycle(
			"forward", "2000-01-01 00:00:00", 7, 3,
			List.of(new MajorLuckPeriod(1999, 2008, 8, 17, "己酉"))
		));

		assertThat(cycle.direction()).isEqualTo("forward");
		assertThat(cycle.periods().getFirst().ganZhi()).isEqualTo("기유");
		assertThat(SajuKoreanTerms.luckCycle(null)).isNull();
	}

	@Test
	void readsTraditionalVariantsAndLeavesKoreanTermsAsTheyAre() {
		assertThat(SajuKoreanTerms.tenGod("劫財")).isEqualTo("겁재");
		assertThat(SajuKoreanTerms.tenGod("七殺")).isEqualTo("편관");
		assertThat(SajuKoreanTerms.tenGod("상관")).isEqualTo("상관");
		assertThat(SajuKoreanTerms.ganZhi("을축")).isEqualTo("을축");
		assertThat(SajuKoreanTerms.stem(null)).isNull();
	}
}
