package com.myeongro.api.aipreview;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.service.*;
import com.myeongro.api.domain.saju.annual.*;
import com.myeongro.api.domain.saju.calculation.SajuMonthlyCalendarService;
import com.myeongro.api.domain.saju.service.SajuPromptCatalog;
import com.myeongro.api.domain.tarot.service.TarotPromptCatalog;

@ExtendWith(OutputCaptureExtension.class)
class SajuAnnualCliFailureTests {
	@TempDir Path directory;
	@Test void cliErrorOutputAndSavedMetricsDoNotContainModelExcerpt(CapturedOutput output) throws Exception {
		var mapper = new ObjectMapper().findAndRegisterModules();
		var snapshot = Map.<String, Object>of("targetYear", 2027, "pillars", Map.of(), "fiveElements", Map.of(),
			"relations", List.of(), "limitations", List.of(), "uncertainty", Map.of(),
			"annualFortune", Map.of("year", 2027, "ganZhi", "정미"),
			"monthlyCalendar", mapper.convertValue(new SajuMonthlyCalendarService().calculate(2027, null), Map.class));
		var previewCase = new AiPreviewCase("annual-cli-test", ReadingKind.SAJU, null, "", Map.of(
			"reportType", "annual_monthly", "targetYear", 2027, "focusArea", "self", "calculationSnapshot", snapshot));
		var loader = mock(AiPreviewCaseLoader.class);
		when(loader.load("saju", "annual-cli-test")).thenReturn(previewCase);
		String marker = "SYNTHETIC_MODEL_OUTPUT_MARKER";
		var input = SajuAnnualInput.from("", "self", 2027, snapshot, mapper);
		var section = Map.of("heading", "평가 제목", "body", "평가 설명", "evidenceKeys", List.of("annualFlow"));
		var months = new java.util.LinkedHashMap<String, Object>();
		for (var month : input.calendar().months()) months.put("m%02d".formatted(month.month()),
			Map.of("heading", "월 제목", "body", "월 설명", "evidenceKeys",
				month.periodIds().stream().map(id -> "monthlyFlow." + id).toList()));
		var reading = new java.util.LinkedHashMap<String, Object>();
		reading.put("reportType", "annual_monthly"); reading.put("detailLevel", "brief"); reading.put("targetYear", marker);
		reading.put("readingMode", "standard"); reading.put("questionRedirected", false); reading.put("title", "평가 제목");
		reading.put("annualOverview", section); reading.put("months", months); reading.put("periods", List.of());
		reading.put("questionReading", null); reading.put("summary", "요약"); reading.put("disclaimer", "안내");
		String response = mapper.writeValueAsString(Map.of("output", Map.of("resultType", "reading", "reading", reading)));
		ChatModel model = prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage(
			response),
			ChatGenerationMetadata.builder().finishReason("STOP").build())));
		var prompts = new SajuAnnualPromptCatalog(new ReadingSafetyPrompt(new ClassPathResource("prompts/reading/safety/reading-safety-ko-v3.md")),
			new ClassPathResource("prompts/saju/common-ko-v11.md"),
			new ClassPathResource("prompts/saju/interpretation/interpretation-guide-ko-v7.md"),
			new ClassPathResource("prompts/saju/reports/annual-monthly/annual-monthly-ko-v5.md"));
		var generator = new OpenAiSajuAnnualGenerator(model, mapper, "test", prompts, new DeclinedReadingFactory());
		var writer = new AiPreviewReportWriter(mapper, new AiPreviewMarkdownRenderer(ZoneId.of("Asia/Seoul")), directory);
		var runner = new AiPreviewRunner(mock(ReadingGenerator.class), mock(TarotPromptCatalog.class), mock(SajuPromptCatalog.class),
			loader, writer, new AiPreviewLabelFactory(Clock.systemUTC()), generator, prompts, mapper,
			"saju", "annual-cli-test", true, "test");
		assertThatThrownBy(runner::run).isInstanceOf(SajuAnnualGenerationException.class).satisfies(exception -> {
			// CLI/Spring receive this same sanitized throwable; render its entire chain to captured stderr.
			exception.printStackTrace(System.err);
		});
		assertThat(output.getAll()).contains("Rejected annual output metrics", "Annual response rejected").doesNotContain(marker);
		try (var files = java.nio.file.Files.walk(directory)) {
			for (var file : files.filter(java.nio.file.Files::isRegularFile).toList()) {
				assertThat(java.nio.file.Files.readString(file)).doesNotContain(marker);
			}
		}
	}
}
