package com.myeongro.api.domain.saju.annual;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.saju.controller.SajuReadingCreateRequest;
import com.myeongro.api.domain.saju.model.SajuBirthProfileRequest;
import com.myeongro.api.domain.saju.place.SajuBirthPlaceCatalog;
import com.myeongro.api.domain.saju.service.*;
import com.myeongro.api.domain.reading.service.*;
import com.myeongro.api.domain.reading.dto.GeneratedReading;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.readingcredit.ReadingCreditTestFixtures;

class SajuAnnualProductionTests {
  final ObjectMapper mapper = new ObjectMapper();
  final SajuReadingInputNormalizer normalizer = new SajuReadingInputNormalizer(new SajuBirthPlaceCatalog(mapper, new ClassPathResource("saju/birth-places/kr-admin-v1.json")));
  SajuReadingCreateRequest request(String type, String question) {
    return new SajuReadingCreateRequest(question, UUID.randomUUID(), new SajuBirthProfileRequest("solar", "1992-08-17", null, "unknown", null, "unspecified"), "career", 2027, type);
  }
  @Test void optionalQuestionAndReportDiscriminatorDoNotChangeComprehensiveFingerprint() {
    var annual = normalizer.normalize(request("annual_monthly", null), 2026);
    assertThat(annual.schemaVersion()).isEqualTo(7);
    assertThat(annual.question()).isEmpty();
    assertThat(annual.idempotencyPayload()).containsEntry("reportType", "annual_monthly");
    assertThat(normalizer.normalize(request("annual_monthly", "  "), 2026).idempotencyPayload()).isEqualTo(annual.idempotencyPayload());
    var defaultInput = normalizer.normalize(request(null, "올해 흐름"), 2026);
    assertThat(defaultInput.schemaVersion()).isEqualTo(6);
    assertThat(defaultInput.idempotencyPayload()).doesNotContainKey("reportType")
      .isEqualTo(normalizer.normalize(request("birth_annual_question", "올해 흐름"), 2026).idempotencyPayload());
    assertThatThrownBy(() -> normalizer.normalize(request(null, " "), 2026)).isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> normalizer.normalize(request("invalid", ""), 2026)).isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> normalizer.normalize(request("annual_monthly", "가".repeat(301)), 2026)).isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> new DirectIdentifierInputGuard().validate(normalizer.normalize(request("annual_monthly", "010-1234-5678 연락처"), 2026))).isInstanceOf(RuntimeException.class);
  }
  @Test void routesAnnualGenerationAndMetadataWithoutRegisteringASecondSajuHandler() {
    var generator = mock(OpenAiSajuAnnualGenerator.class);
    var catalog = mock(SajuAnnualPromptCatalog.class);
    when(catalog.version()).thenReturn("annual-test-version");
    var reading = new GeneratedReading("한 해 리포트", Map.of("reportType", "annual_monthly"));
    when(generator.generate(any())).thenReturn(new OpenAiSajuAnnualGenerator.Evaluation(reading, Map.of()));
    var handler = new OpenAiSajuReadingGenerator(mock(org.springframework.ai.chat.model.ChatModel.class), mapper, "configured-model", mock(SajuPromptCatalog.class), new SajuReadingResultValidator(), mock(DeclinedReadingFactory.class), generator, catalog);
    var payload = Map.<String,Object>of("reportType", "annual_monthly", "targetYear", 2027, "focusArea", "career", "calculationSnapshot", SajuAnnualTests.snapshot("exact", "12:00", "1992-08-17"));
    assertThat(handler.generate(ReadingKind.SAJU, null, "", payload)).isSameAs(reading);
    assertThat(handler.metadata(null, payload).promptVersion()).isEqualTo("annual-test-version");
    var captor = org.mockito.ArgumentCaptor.forClass(SajuAnnualInput.class);
    verify(generator).generate(captor.capture());
    assertThat(captor.getValue().question()).isEmpty();
    assertThat(captor.getValue().trustedCalculation().toString()).doesNotContain("birthDate", "birthTime", "startInclusive");
  }
  @Test void approvedPriceIsFourForBothReportTypes() {
    var prices = ReadingCreditTestFixtures.properties();
    assertThat(prices.cost(ReadingKind.SAJU, null, "annual_monthly")).isEqualTo(4);
    assertThat(prices.cost(ReadingKind.SAJU, null, "birth_annual_question")).isEqualTo(4);
  }
}
