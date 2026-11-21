package com.myeongro.api.aipreview;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import com.myeongro.api.domain.reading.dto.GeneratedReading;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.service.ReadingGenerator;
import com.myeongro.api.domain.saju.annual.OpenAiSajuAnnualGenerator;
import com.myeongro.api.domain.saju.annual.SajuAnnualPromptCatalog;
import com.myeongro.api.domain.saju.service.SajuPromptCatalog;
import com.myeongro.api.domain.tarot.service.TarotPromptCatalog;

@ExtendWith(OutputCaptureExtension.class)
class AiPreviewBirthRunnerTests {
	@TempDir Path directory;
	@Test void dryRunCalculatesButDoesNotGenerateOrPrintBirthData(CapturedOutput output) {
		var generator = mock(ReadingGenerator.class);
		runner(generator, false).run();
		verifyNoInteractions(generator);
		assertThat(output.getAll()).contains("OpenAI calls: 0", "calculation version:", "engine version:")
			.doesNotContain("1988-05-05", "12:00", "birthProfile");
		assertThat(directory).isEmptyDirectory();
	}
	@Test void executionForwardsOneCalculatedPayloadAndStoresOnlySafeMetadata() throws Exception {
		var generator = mock(ReadingGenerator.class);
		when(generator.generate(eq(ReadingKind.SAJU), isNull(), anyString(), anyMap()))
			.thenReturn(new GeneratedReading("합성 결과", Map.of("title", "합성 결과")));
		runner(generator, true).run();
		@SuppressWarnings("unchecked") ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
		verify(generator, times(1)).generate(eq(ReadingKind.SAJU), isNull(), anyString(), payload.capture());
		assertThat(payload.getValue()).containsKey("calculation").doesNotContainKey("birthProfile");
		try (var files = java.nio.file.Files.walk(directory)) {
			var json = files.filter(p -> p.toString().endsWith(".json")).findFirst().orElseThrow();
			String saved = java.nio.file.Files.readString(json);
			assertThat(saved).contains("calculationVersion", "engineVersion", "birth_profile")
				.doesNotContain("1988-05-05", "12:00", "birthDate", "provinceCode");
		}
	}
	private AiPreviewRunner runner(ReadingGenerator generator, boolean execute) {
		var source = AiPreviewSajuInputPreparerTests.birthCase("exact", false);
		var loader = mock(AiPreviewCaseLoader.class);
		when(loader.load("saju", "synthetic-birth")).thenReturn(source);
		var prompts = mock(SajuPromptCatalog.class);
		when(prompts.prompt()).thenReturn("synthetic prompt"); when(prompts.version()).thenReturn("synthetic-version");
		var mapper = AiPreviewSajuInputPreparerTests.MAPPER;
		return new AiPreviewRunner(generator, mock(TarotPromptCatalog.class), prompts, loader,
			new AiPreviewReportWriter(mapper, new AiPreviewMarkdownRenderer(ZoneId.of("Asia/Seoul")), directory),
			new AiPreviewLabelFactory(Clock.systemUTC()), mock(OpenAiSajuAnnualGenerator.class),
			mock(SajuAnnualPromptCatalog.class), mapper, AiPreviewSajuInputPreparerTests.preparer(),
			new AiPreviewChatMetrics(), "saju", "synthetic-birth", execute, "test");
	}
}
