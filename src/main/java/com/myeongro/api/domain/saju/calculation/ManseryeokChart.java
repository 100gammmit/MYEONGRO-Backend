package com.myeongro.api.domain.saju.calculation;

import java.util.List;
import java.util.Map;

/** Public-only projection. No raw birth profile and no persistence/AI coupling. */
public record ManseryeokChart(
	int schemaVersion, String calculationVersion, String engine, String engineVersion,
	String timeCorrectionVersion, String timeZoneRulesVersion, String cityCatalogVersion, int referenceYear,
	AppliedOptions appliedOptions, Chart chart, Map<String,Integer> fiveElements,
	CollectionValue<PositionedRelation> relations, Map<String,List<HiddenStem>> hiddenStems,
	Map<String,Symbol> twelveStages, Map<String,Symbol> voids, MajorLuck luckCycle,
	List<AnnualFlow> annualFlows, SajuMonthlyCalendar monthlyCalendar,
	SajuCalculationSnapshot.TimeCorrection timeCorrection, List<String> limitations,
	SajuCalculationSnapshot.Uncertainty uncertainty, Map<String,Capability> interpretationCapabilities,
	String calculationFingerprint
) {
	public record Symbol(String hanja, String korean) { }
	public record Chart(Map<String,ChartPillar> pillars, Symbol dayMaster) { }
	public record ChartPillar(Symbol ganZhi, Symbol stem, Symbol branch, String stemTenGod) { }
	public record HiddenStem(Symbol stem, String tenGod) { }
	public record Member(String position, Symbol symbol) { }
	public record PositionedRelation(String type, List<Member> members) { }
	public record CollectionValue<T>(String status, List<T> values, List<String> reasonCodes) { }
	public record Capability(String status, List<String> fields, List<String> reasonCodes) { }
	public record MajorLuck(SajuCalculationSnapshot.LuckCycle precise, int integerStartAge, String integerMethod) { }
	public record AnnualFlow(int year, Symbol ganZhi, String stemTenGod) { }
	public record AppliedOptions(SajuCalculationOptions.DayBoundary dayBoundary, boolean equationOfTime,
		String coordinateSource, double longitude, boolean birthTimeKnown) { }
}
