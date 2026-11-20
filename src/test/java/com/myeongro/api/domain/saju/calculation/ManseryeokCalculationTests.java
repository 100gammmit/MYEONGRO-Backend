package com.myeongro.api.domain.saju.calculation;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.saju.controller.SajuCalculationRequest;
import com.myeongro.api.domain.saju.service.ManseryeokCalculationService;
import com.myeongro.api.domain.saju.place.SajuBirthPlaceCatalog;

class ManseryeokCalculationTests {
	static final ObjectMapper MAPPER = new ObjectMapper();
	static final SajuBirthPlaceCatalog CATALOG = new SajuBirthPlaceCatalog(MAPPER,new ClassPathResource("saju/birth-places/kr-admin-v1.json"));
	static ManseryeokCalculationService service() {
		var corrector = new TrueSolarTimeCorrector();
		return new ManseryeokCalculationService(new SajuCalculationService(CATALOG,corrector,new ApproximateBirthTimeResolver(corrector,new LunarJavaFourPillarsAdapter())),CATALOG,
			new ManseryeokChartBuilder(),new SajuMonthlyCalendarService(),MAPPER,Clock.fixed(Instant.parse("2026-11-16T00:00:00Z"),ZoneOffset.UTC));
	}
	static SajuCalculationRequest request(String date, String time, String precision, String options) throws Exception {
		return MAPPER.readValue("{\"birthProfile\":{\"calendarType\":\"solar\",\"birthDate\":\""+date+"\",\"birthTimePrecision\":\""+precision+"\",\"luckDirectionBasis\":\"male\""+
			(time==null ? "" : ",\"birthTime\":\""+time+"\",\"provinceCode\":\"11\"")+"}"+(options==null ? "" : ",\"options\":"+options)+"}",SajuCalculationRequest.class);
	}
	@Test void preciseCorrectionUsesInstantAndPreservesSecondsAndTheLegacyPath() {
		var corrector = new TrueSolarTimeCorrector();
		var civil = LocalDateTime.parse("1992-08-18T00:36:00");
		assertThat(corrector.correct(civil,127.0004).trueSolarTime()).isEqualTo(LocalDateTime.parse("1992-08-18T00:00:42"));
		var settings = SajuCalculationSettings.precise(SajuCalculationOptions.DEFAULT);
		assertThat(corrector.correctCandidates(civil,127.0004,settings).getFirst().trueSolarTime()).isEqualTo(LocalDateTime.parse("1992-08-18T00:00:03"));
		assertThat(corrector.correctCandidates(civil,126.98,settings).getFirst().trueSolarTime()).isEqualTo(LocalDateTime.parse("1992-08-17T23:59:58"));
		var off = corrector.correctCandidates(civil,127.0004,SajuCalculationSettings.precise(new SajuCalculationOptions(SajuCalculationOptions.DayBoundary.MIDNIGHT,false))).getFirst();
		assertThat(off.equationOfTimeMinutes()).isZero();
		assertThat(off.longitudeCorrectionMinutes()).isNotZero();
	}
	@Test void noaaFixedNumbersEpochsAndIndependentUsnoGrid() {
		assertThat(PreciseEquationOfTime.julianDate(Instant.EPOCH)).isEqualTo(2440587.5);
		assertThat(PreciseEquationOfTime.julianDate(Instant.parse("2000-01-01T12:00:00Z"))).isEqualTo(2451545);
		assertThat(PreciseEquationOfTime.minutes(Instant.parse("1992-08-17T15:36:00Z"))).isCloseTo(-3.945489,within(0.1/60));
		assertThat(PreciseEquationOfTime.minutes(Instant.parse("2020-02-04T09:00:00Z"))).isCloseTo(-13.861996,within(0.1/60));
		double max=0; int count=0;
		for (var date=LocalDate.of(1900,1,1);date.isBefore(LocalDate.of(2100,1,1));date=date.plusDays(1)) for (int hour : new int[]{0,6,12,18}) {
			var civil=date.atTime(hour,0);
			for (var offset:SajuCalculationRules.BIRTH_ZONE.getRules().getValidOffsets(civil)) {
				var instant=civil.toInstant(offset); max=Math.max(max,60*Math.abs(PreciseEquationOfTime.minutes(instant)-usno(instant))); count++;
			}
		}
		assertThat(count).isEqualTo(292183); assertThat(max).isLessThanOrEqualTo(5);
	}
	// Separate USNO solar coordinate approximation: https://aa.usno.navy.mil/faq/sun_approx
	static double usno(Instant instant) {
		double days=PreciseEquationOfTime.julianDate(instant)-2451545;
		double g=Math.toRadians(wrap(357.529+0.98560028*days)),q=wrap(280.459+0.98564736*days);
		double l=Math.toRadians(wrap(q+1.915*Math.sin(g)+0.020*Math.sin(2*g))),e=Math.toRadians(23.439-0.00000036*days);
		double ra=wrap(Math.toDegrees(Math.atan2(Math.cos(e)*Math.sin(l),Math.cos(l))));
		return (wrap(q-ra+180)-180)*4;
	}
	static double wrap(double degrees) { return (degrees%360+360)%360; }
	@Test void publicChartHasDerivedFieldsAndReproducibleFingerprint() throws Exception {
		var service=service(); var request=request("2000-06-15","14:30","exact",null);
		var chart=service.calculate(request);
		assertThat(chart.schemaVersion()).isEqualTo(1); assertThat(chart.calculationVersion()).isEqualTo("manseryeok-ko-v1");
		assertThat(chart.chart().pillars().get("day").ganZhi().korean()).isEqualTo("갑진");
		assertThat(chart.hiddenStems().get("day")).isNotEmpty(); assertThat(chart.twelveStages().get("day")).isNotNull();
		assertThat(chart.voids().get("day")).isNotNull(); assertThat(chart.luckCycle().integerStartAge()).isGreaterThanOrEqualTo(0);
		assertThat(chart.annualFlows()).hasSize(10); assertThat(chart.monthlyCalendar().months()).hasSize(12);
		assertThat(chart.calculationFingerprint()).matches("[0-9a-f]{64}").isEqualTo(service.calculate(request).calculationFingerprint());
		assertThat(chart.timeCorrectionVersion()).isEqualTo("meeus-noaa-v1");
		assertThat(MAPPER.writeValueAsString(chart)).doesNotContain("birthProfile", "question", "userId");
	}
	@Test void unknownHasNoPersonalGodsOrInventedTimeAndOverlapsRemainAmbiguous() throws Exception {
		var unknown=service().calculate(request("1992-08-17",null,"unknown",null));
		assertThat(unknown.chart().dayMaster()).isNull(); assertThat(unknown.chart().pillars().get("time")).isNull();
		assertThat(unknown.luckCycle()).isNull(); assertThat(unknown.timeCorrection()).isNull();
		assertThat(unknown.annualFlows()).allMatch(v->v.stemTenGod()==null);
		assertThat(unknown.hiddenStems().values()).allMatch(v->v==null || v.stream().allMatch(h->h.tenGod()==null));
		assertThat(unknown.twelveStages().values()).allMatch(Objects::isNull);
		assertThat(unknown.voids().get("day")).isNull();
		assertThat(unknown.voids().get("year")).isNotNull();
		assertThat(unknown.uncertainty().varyingFields()).contains("chart.dayMaster","chart.pillars.day","chart.pillars.time","voids.day","annualFlows.*.stemTenGod","monthlyCalendar.months.*.periods.*.stemTenGod");
		assertThat(unknown.uncertainty().varyingFields()).noneMatch(f->f.startsWith("pillars.")||f.startsWith("annualFortune."));
		var termUnknown=service().calculate(request("2020-02-04",null,"unknown",null));
		assertThat(termUnknown.chart().pillars().get("year")).isNull();
		assertThat(termUnknown.voids().get("year")).isNull();
		assertThat(termUnknown.uncertainty().varyingFields()).contains("chart.pillars.year","voids.year");
		assertThat(unknown.interpretationCapabilities().get("annual_personal").status()).isEqualTo("limited");
		var overlap=service().calculate(request("1988-10-09","02:30","exact",null));
		assertThat(overlap.uncertainty().candidateZoneOffsets()).hasSize(2); assertThat(overlap.luckCycle()).isNull();
		assertThatThrownBy(()->service().calculate(request("1988-05-08","02:30","exact",null))).isInstanceOf(com.myeongro.api.domain.reading.exception.InvalidReadingRequestException.class);
	}
	@Test void optionsAndCityHaveAnExplicitEffect() throws Exception {
		var midnight=service().calculate(request("1992-08-17","23:50","exact",null));
		var hour23=service().calculate(request("1992-08-17","23:50","exact","{\"dayBoundary\":\"HOUR_23\"}"));
		assertThat(midnight.chart().pillars().get("day")).isNotEqualTo(hour23.chart().pillars().get("day"));
		assertThat(midnight.chart().pillars().get("time")).isNotEqualTo(null);
		assertThat(midnight.chart().pillars().get("time").ganZhi()).isEqualTo(hour23.chart().pillars().get("time").ganZhi());
		var tree=MAPPER.valueToTree(request("2000-06-15","14:30","exact",null));
		((com.fasterxml.jackson.databind.node.ObjectNode)tree).remove(List.of("options","referenceYear"));
		((com.fasterxml.jackson.databind.node.ObjectNode)tree.get("birthProfile")).put("cityCode",CATALOG.publicProvinces().getFirst().cities().getFirst().cityCode());
		var city=service().calculate(MAPPER.treeToValue(tree,SajuCalculationRequest.class));
		assertThat(city.appliedOptions().coordinateSource()).isEqualTo("city");
		assertThat(city.appliedOptions().longitude()).isNotEqualTo(midnight.appliedOptions().longitude());
	}
	@Test void sharedVoidOrStageValuesStillRequireTheirConfirmedBasis() throws Exception {
		for (String precision : List.of("unknown","approximate")) {
			var result=service().calculate(request("2000-06-16",precision.equals("unknown")?null:"00:30",precision,null));
			assertThat(result.chart().pillars().get("day")).isNull();
			assertThat(result.voids().get("day")).isNull();
			assertThat(result.voids().get("year")).isNotNull();
			assertThat(result.uncertainty().varyingFields()).contains("voids.day");
			assertThat(result.hiddenStems().get("year")).isNotEmpty().allMatch(h->h.tenGod()==null);
			assertThat(result.hiddenStems().get("month")).isNotEmpty().allMatch(h->h.tenGod()==null);
			assertThat(result.uncertainty().varyingFields()).contains("hiddenStems.year.*.tenGod","hiddenStems.month.*.tenGod")
				.noneMatch(f->f.matches("hiddenStems\\.[^.]+\\.tenGod"));
		}
		// Adjacent Yi/Bing day masters happen to share the Chen stage; a common derived
		// stage must nevertheless be hidden and marked when the day-master basis is unknown.
		var result=service().calculate(request("2000-06-17",null,"unknown",null));
		assertThat(result.chart().dayMaster()).isNull();
		assertThat(result.twelveStages().get("year")).isNull();
		assertThat(result.uncertainty().varyingFields()).contains("chart.dayMaster","twelveStages.year");
	}
}
