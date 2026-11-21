package com.myeongro.api.aipreview;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import com.myeongro.api.domain.reading.dto.GeneratedReading;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.service.ReadingGenerator;
import com.myeongro.api.domain.saju.annual.OpenAiSajuAnnualGenerator;
import com.myeongro.api.domain.saju.annual.SajuAnnualPromptCatalog;
import com.myeongro.api.domain.saju.service.SajuPromptCatalog;
import com.myeongro.api.domain.tarot.model.TarotSpreadType;
import com.myeongro.api.domain.tarot.service.TarotPromptCatalog;

class AiPreviewUsageRunnerTests {
	@TempDir Path directory;

	@ParameterizedTest
	@CsvSource({"tarot,false", "saju,false", "annual,false", "tarot,true", "saju,true"})
	void savesUsageOnSuccessAndPostResponseFailureWithoutCopyingProviderBody(String kind, boolean fail) throws Exception {
		var mapper = AiPreviewSajuInputPreparerTests.MAPPER;
		var tap = new AiPreviewChatMetrics();
		var provider = mock(ChatModel.class);
		when(provider.call(any(Prompt.class))).thenReturn(AiPreviewChatMetricsTests.response(100, 20));
		var measured = (ChatModel)tap.postProcessAfterInitialization(provider, "provider");
		var generator = mock(ReadingGenerator.class);
		var failure = new IllegalArgumentException("synthetic parser error");
		when(generator.generate(any(), any(), anyString(), anyMap())).thenAnswer(invocation -> {
			measured.call(new Prompt("synthetic question"));
			if (fail) throw failure;
			return new GeneratedReading("합성 결과", Map.of("title", "합성 결과"));
		});
		boolean annual = "annual".equals(kind);
		String cliKind = annual ? "saju" : kind;
		var annualGenerator = mock(OpenAiSajuAnnualGenerator.class);
		when(annualGenerator.generate(any())).thenAnswer(invocation -> {
			measured.call(new Prompt("synthetic question"));
			return new OpenAiSajuAnnualGenerator.Evaluation(new GeneratedReading("합성 결과", Map.of("title", "합성 결과")), Map.of());
		});
		var source = "saju".equals(cliKind) ? AiPreviewSajuInputPreparerTests.birthCase("exact", annual)
			: new AiPreviewCase("synthetic", ReadingKind.TAROT, TarotSpreadType.MIND_THREE_CARD,
				"합성 질문", Map.of("cards", List.of(Map.of("cardId", "major-00-fool"))));
		var loader = mock(AiPreviewCaseLoader.class); when(loader.load(cliKind, "synthetic")).thenReturn(source);
		var tarot = mock(TarotPromptCatalog.class); when(tarot.prompt(any(), anyList())).thenReturn("synthetic prompt");
		when(tarot.version(any())).thenReturn("test");
		var saju = mock(SajuPromptCatalog.class); when(saju.prompt()).thenReturn("synthetic prompt"); when(saju.version()).thenReturn("test");
		var annualPrompts = mock(SajuAnnualPromptCatalog.class);
		when(annualPrompts.prompt()).thenReturn("synthetic prompt"); when(annualPrompts.version()).thenReturn("test");
		var runner = new AiPreviewRunner(generator, tarot, saju, loader,
			new AiPreviewReportWriter(mapper, new AiPreviewMarkdownRenderer(ZoneId.of("Asia/Seoul")), directory),
			new AiPreviewLabelFactory(Clock.systemUTC()), annualGenerator,
			annualPrompts, mapper, AiPreviewSajuInputPreparerTests.preparer(), tap,
			cliKind, "synthetic", true, "configured-model");
		if (fail) assertThatThrownBy(runner::run).isSameAs(failure); else runner.run();
		try (var paths = Files.walk(directory)) {
			Path file = paths.filter(p -> p.toString().endsWith(".json")).findFirst().orElseThrow();
			String saved = Files.readString(file);
			var report = mapper.readTree(saved);
			assertThat(report.path("metrics").path("promptTokens").intValue()).isEqualTo(100);
			assertThat(report.path("metrics").path("completionTokens").intValue()).isEqualTo(20);
			assertThat(report.path("metrics").path("totalTokens").intValue()).isEqualTo(120);
			assertThat(report.path("metrics").path("responseModel").textValue()).isEqualTo("actual-model");
			assertThat(saved).doesNotContain("SYNTHETIC_BODY", "synthetic parser error", "synthetic question");
			if (fail) assertThat(report.path("result").path("resultType").textValue()).isEqualTo("failed");
		}
		assertThat(tap.merge(Map.of())).isEmpty();
		verify(provider, times(1)).call(any(Prompt.class));
	}
}
