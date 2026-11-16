package com.myeongro.api.domain.saju.calculation;

import static org.assertj.core.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import com.myeongro.api.domain.reading.exception.InvalidReadingRequestException;

class ManseryeokExternalReferenceTests {
	private List<String> rows(String name) throws IOException {
		try(var reader=new BufferedReader(new InputStreamReader(new ClassPathResource("saju/manseryeok/"+name).getInputStream(),StandardCharsets.UTF_8))) {
			return reader.lines().filter(s->!s.isBlank()&&!s.startsWith("#")).toList();
		}
	}
	@Test void thirtySixOfficialTermsAgreeWithinOneMinuteAndPublicPillarsChangeAcrossThem() throws Exception {
		var rows=rows("kasi-jie-kst.csv"); assertThat(rows).hasSize(36);
		var monthly=new SajuMonthlyCalendarService(); var service=ManseryeokCalculationTests.service(); long maximum=0;
		for(String row:rows) {
			var official=OffsetDateTime.parse(row);
			var month=monthly.calculate(official.getYear(),null).months().get(official.getMonthValue()-1);
			assertThat(month.periods()).hasSize(2);
			var computed=OffsetDateTime.parse(month.periods().getFirst().endExclusive());
			long difference=Math.abs(Duration.between(official,computed).getSeconds()); maximum=Math.max(maximum,difference);
			assertThat(difference).as(row).isLessThan(60);
			var before=official.minusMinutes(2); var after=official.plusMinutes(2);
			var first=service.calculate(ManseryeokCalculationTests.request(before.toLocalDate().toString(),before.toLocalTime().toString(),"exact",null));
			var second=service.calculate(ManseryeokCalculationTests.request(after.toLocalDate().toString(),after.toLocalTime().toString(),"exact",null));
			assertThat(first.chart().pillars().get("month").ganZhi()).as(row).isNotEqualTo(second.chart().pillars().get("month").ganZhi());
			assertThat(first.chart().pillars().get("year").ganZhi().equals(second.chart().pillars().get("year").ganZhi())).isEqualTo(official.getMonthValue()!=2);
		}
		System.out.println("KASI_OFFICIAL_TERMS count=36 maxDifferenceSeconds="+maximum);
	}
	@Test void thirtyExternalUiCasesMatchOrHaveAnExplicitTestedPolicyDifference() throws Exception {
		var rows=rows("external-charts.csv"); assertThat(rows).hasSize(30); var service=ManseryeokCalculationTests.service();
		for(String row:rows) {
			String[] f=row.split(",",-1); var request=ManseryeokCalculationTests.request(f[1],f[2].isEmpty()?null:f[2],f[3],null);
			if(f[8].equals("dst-gap-policy")) {
				assertThatThrownBy(()->service.calculate(request)).isInstanceOfSatisfying(InvalidReadingRequestException.class,e->assertThat(e.getCode()).isEqualTo("INVALID_BIRTH_TIME")); continue;
			}
			var chart=service.calculate(request); int index=4;
			for(String position:List.of("year","month","day","time")) {
				var pillar=chart.chart().pillars().get(position); String external=f[index++];
				if(f[8].equals("longitude-boundary")&&position.equals("day")) {
					assertThat(pillar.ganZhi().korean()).isEqualTo("병인");
					var corrector=new TrueSolarTimeCorrector(); var correction=corrector.correctCandidates(LocalDateTime.parse(f[1]+"T"+f[2]),126.98,SajuCalculationSettings.precise(SajuCalculationOptions.DEFAULT)).getFirst();
					var matched=new LunarJavaFourPillarsAdapter().calculate(correction.trueSolarTime(),correction.engineCivilTime(),true,com.myeongro.api.domain.saju.model.LuckDirectionBasis.MALE,2026);
					assertThat(SajuKoreanTerms.ganZhi(matched.pillars().day().ganZhi())).isEqualTo(external);
				} else if(pillar!=null) assertThat(pillar.ganZhi().korean()).as(row+" "+position).isEqualTo(external);
			}
			if(f[8].equals("unknown-policy")) { assertThat(chart.chart().pillars().get("time")).isNull(); assertThat(chart.chart().dayMaster()).isNull(); }
			if(f[8].contains("overlap")) { assertThat(chart.uncertainty().candidateZoneOffsets()).hasSize(2); assertThat(chart.luckCycle()).isNull(); }
			if(f[3].equals("approximate")) assertThat(chart.uncertainty().candidateCount()).isGreaterThanOrEqualTo(121);
		}
	}
}
