package com.myeongro.api.domain.saju.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.service.NormalizedReadingInput;
import com.myeongro.api.domain.reading.service.ReadingSchemaVersions;
import com.myeongro.api.domain.saju.calculation.SajuCalculationService;
import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot;
import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot.AnnualFortune;
import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot.Pillar;
import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot.Pillars;
import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot.Uncertainty;

class SajuReadingInputAssemblerTests {

	private final SajuCalculationService calculationService =
		org.mockito.Mockito.mock(SajuCalculationService.class);
	private final SajuReadingInputAssembler assembler =
		new SajuReadingInputAssembler(calculationService, new ObjectMapper());

	@Test
	void freezesTargetYearAndVersionedSnapshotWithoutChangingIdempotencyMaterial() {
		NormalizedReadingInput base = baseInput();
		when(calculationService.calculate(profile(), 2026)).thenReturn(snapshot("kr-admin-v1"));

		NormalizedReadingInput assembled = assembler.assemble(base, 2026);

		assertThat(assembled.payload()).containsKeys(
			"question", "focusArea", "birthProfile", "targetYear", "calculationSnapshot"
		);
		assertThat(assembled.payload()).containsEntry("targetYear", 2026);
		assertThat(assembled.hashMaterial()).isEqualTo(base.hashMaterial());
		assertThat(((Map<?, ?>)assembled.payload().get("calculationSnapshot"))
			.get("cityCatalogVersion")).isEqualTo("kr-admin-v1");
	}

	@Test
	void restoresStoredSnapshotWithoutRecalculationAndAcceptsHistoricalCatalogVersion() {
		NormalizedReadingInput base = baseInput();
		when(calculationService.calculate(profile(), 2026)).thenReturn(snapshot("kr-admin-v0"));
		NormalizedReadingInput stored = assembler.assemble(base, 2026);

		NormalizedReadingInput restored = assembler.restore(base, stored.payload());

		assertThat(restored.payload()).isEqualTo(stored.payload());
		org.mockito.Mockito.verify(calculationService).calculate(profile(), 2026);
		org.mockito.Mockito.verifyNoMoreInteractions(calculationService);
	}

	@Test
	@SuppressWarnings("unchecked")
	void rejectsSnapshotWithUnknownFieldsBeforeRetry() {
		NormalizedReadingInput base = baseInput();
		when(calculationService.calculate(profile(), 2026)).thenReturn(snapshot("kr-admin-v1"));
		Map<String, Object> stored = new LinkedHashMap<>(assembler.assemble(base, 2026).payload());
		Map<String, Object> snapshot = new LinkedHashMap<>((Map<String, Object>)stored.get("calculationSnapshot"));
		snapshot.put("unexpected", true);
		stored.put("calculationSnapshot", snapshot);

		assertThatThrownBy(() -> assembler.restore(base, stored))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("snapshot");
	}

	private NormalizedReadingInput baseInput() {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("question", "올해 흐름이 궁금해요");
		payload.put("focusArea", "career");
		payload.put("birthProfile", profile());
		return new NormalizedReadingInput(
			ReadingKind.SAJU, null, ReadingSchemaVersions.SAJU,
			"올해 흐름이 궁금해요", payload,
			Map.of("kind", "saju", "inputPayload", payload)
		);
	}

	private Map<String, Object> profile() {
		return Map.of(
			"calendarType", "solar",
			"birthDate", "1992-08-17",
			"birthTime", "12:00",
			"birthTimePrecision", "exact",
			"provinceCode", "36",
			"cityCode", "36110",
			"luckDirectionBasis", "male"
		);
	}

	private SajuCalculationSnapshot snapshot(String catalogVersion) {
		Pillar pillar = new Pillar("壬申", "壬", "申", "watermetal", "正印", List.of("正官"));
		return new SajuCalculationSnapshot(
			"saju-ko-v1", "lunar-java", "1.7.7", catalogVersion, 2026,
			null, new Pillars(pillar, pillar, pillar, pillar), "乙",
			Map.of("wood", 1), List.of(), null,
			new AnnualFortune(2026, "丙午", "伤官"),
			List.of(), new Uncertainty(
				"exact", 1, "1992-08-17T12:00", "1992-08-17T12:00",
				List.of(), List.of("+09:00")
			)
		);
	}
}
