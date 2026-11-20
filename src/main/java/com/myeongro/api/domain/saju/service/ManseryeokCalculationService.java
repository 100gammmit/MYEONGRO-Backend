package com.myeongro.api.domain.saju.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.zone.ZoneRulesProvider;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.security.MessageDigest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.myeongro.api.domain.saju.calculation.*;
import com.myeongro.api.domain.saju.calculation.ManseryeokChart.*;
import com.myeongro.api.domain.saju.controller.SajuCalculationRequest;
import com.myeongro.api.domain.saju.place.SajuBirthPlaceCatalog;
import com.myeongro.api.domain.reading.exception.InvalidReadingRequestException;

@Service
public class ManseryeokCalculationService {
	private final SajuCalculationService calculation;
	private final SajuBirthPlaceCatalog catalog;
	private final ManseryeokChartBuilder builder;
	private final SajuMonthlyCalendarService monthly;
	private final ObjectMapper mapper;
	private final Clock clock;
	private final Semaphore slots = new Semaphore(2);
	@Autowired public ManseryeokCalculationService(SajuCalculationService calculation, SajuBirthPlaceCatalog catalog,
		ManseryeokChartBuilder builder, SajuMonthlyCalendarService monthly, ObjectMapper mapper) {
		this(calculation,catalog,builder,monthly,mapper,Clock.systemUTC());
	}
	public ManseryeokCalculationService(SajuCalculationService calculation, SajuBirthPlaceCatalog catalog,
		ManseryeokChartBuilder builder, SajuMonthlyCalendarService monthly, ObjectMapper mapper, Clock clock) {
		this.calculation=calculation; this.catalog=catalog; this.builder=builder; this.monthly=monthly;
		this.mapper=mapper.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS); this.clock=clock;
	}
	public ManseryeokChart calculate(SajuCalculationRequest request) {
		long deadline = System.nanoTime() + 3_000_000_000L;
		// Validate before taking a slot: invalid input is rejected without holding calculation capacity.
		var input = request.validate(catalog,LocalDate.ofInstant(clock.instant(),SajuCalculationRules.BIRTH_ZONE).getYear());
		int year = input.referenceYear();
		if (!slots.tryAcquire()) throw new Unavailable("SAJU_CALCULATION_BUSY");
		try {
			Runnable check = () -> { if (System.nanoTime() >= deadline || Thread.currentThread().isInterrupted()) throw new Unavailable("SAJU_CALCULATION_DEADLINE"); };
			check.run();
			var accumulated = builder.accumulator();
			var snapshot = calculation.calculateForChart(input.profile(),year,input.longitude(),
				SajuCalculationSettings.precise(input.options()),accumulated,check);
			check.run();
			var calendar = monthly.calculate(year,accumulated.dayMaster());
			var annual = builder.annualFlows(year,accumulated.dayMaster());
			var fields = publicVaryingFields(snapshot.uncertainty().varyingFields());
			fields.addAll(accumulated.varyingFields());
			if (accumulated.dayMaster() == null) fields.add("chart.dayMaster");
			if (accumulated.relations().status().equals("limited")) fields.add("relations");
			var uncertainty = new SajuCalculationSnapshot.Uncertainty(snapshot.uncertainty().precision(),snapshot.uncertainty().candidateCount(),
				snapshot.uncertainty().rangeStart(),snapshot.uncertainty().rangeEnd(),List.copyOf(fields),snapshot.uncertainty().candidateZoneOffsets());
			var result = new ManseryeokChart(1,"manseryeok-ko-v1",SajuCalculationRules.ENGINE,SajuCalculationRules.ENGINE_VERSION,
				"meeus-noaa-v1",ZoneRulesProvider.getVersions("Asia/Seoul").lastKey(),catalog.publicVersion(),year,
				new AppliedOptions(input.options().dayBoundary(),input.options().equationOfTime(),input.coordinateSource(),input.longitude(),!"unknown".equals(input.profile().get("birthTimePrecision"))),
				builder.chart(accumulated),snapshot.fiveElements(),accumulated.relations(),accumulated.hiddenStems(),accumulated.twelveStages(),accumulated.voids(),
				snapshot.luckCycle()==null || accumulated.integerLuck()==null ? null : new MajorLuck(snapshot.luckCycle(),accumulated.integerLuck(),"nearest-year-three-days-v1"),
				annual,calendar,snapshot.timeCorrection(),snapshot.limitations(),uncertainty,builder.capabilities(snapshot,accumulated),null);
			var node = mapper.valueToTree(result);
			((com.fasterxml.jackson.databind.node.ObjectNode)node).remove("calculationFingerprint");
			// Reparse into ordered maps so nested record and map ordering share a canonical representation.
			byte[] canonical = mapper.writeValueAsBytes(mapper.convertValue(node,Map.class));
			String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
			((com.fasterxml.jackson.databind.node.ObjectNode)node).put("calculationFingerprint",fingerprint);
			check.run();
			return mapper.treeToValue(node,ManseryeokChart.class);
		} catch (InvalidReadingRequestException | Unavailable exception) { throw exception; }
		catch (Exception exception) { throw new Failed(); }
		finally { slots.release(); }
	}
	private static LinkedHashSet<String> publicVaryingFields(List<String> internalFields) {
		var fields = new LinkedHashSet<String>();
		for (String field : internalFields) {
			if (field.startsWith("pillars.")) fields.add("chart."+field);
			else if (field.equals("annualFortune.stemTenGod")) {
				fields.add("annualFlows.*.stemTenGod");
				fields.add("monthlyCalendar.months.*.periods.*.stemTenGod");
			} else fields.add(field);
		}
		return fields;
	}
	public static class Unavailable extends RuntimeException {
		private final String code;
		public Unavailable(String code) { super("계산 요청이 많거나 제한시간을 초과했습니다."); this.code=code; }
		public String code() { return code; }
	}
	public static class Failed extends RuntimeException {
		public Failed() { super("만세력 계산을 완료하지 못했습니다."); }
	}
}
