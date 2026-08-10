package com.myeongro.api.aipreview;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

import com.myeongro.api.domain.reading.dto.GeneratedReading;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.service.ReadingGenerator;
import com.myeongro.api.domain.reading.service.SajuPromptCatalog;
import com.myeongro.api.domain.reading.service.TarotPromptCatalog;

@Component
@Profile("ai-preview")
final class AiPreviewRunner {

	private static final Pattern SAFE_LABEL = Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9_-]*");

	private final ReadingGenerator readingGenerator;
	private final TarotPromptCatalog tarotPromptCatalog;
	private final SajuPromptCatalog sajuPromptCatalog;
	private final AiPreviewCaseLoader caseLoader;
	private final TarotPreviewInputValidator tarotInputValidator;
	private final SajuPreviewInputValidator sajuInputValidator;
	private final AiPreviewReportWriter reportWriter;
	private final AiPreviewLabelFactory labelFactory;
	private final String kind;
	private final String caseId;
	private final boolean execute;
	private final String model;

	AiPreviewRunner(
		ReadingGenerator readingGenerator,
		TarotPromptCatalog tarotPromptCatalog,
		SajuPromptCatalog sajuPromptCatalog,
		AiPreviewCaseLoader caseLoader,
		TarotPreviewInputValidator tarotInputValidator,
		SajuPreviewInputValidator sajuInputValidator,
		AiPreviewReportWriter reportWriter,
		AiPreviewLabelFactory labelFactory,
		@Value("${ai-preview.kind:}") String kind,
		@Value("${ai-preview.case:}") String caseId,
		@Value("${ai-preview.execute:false}") boolean execute,
		@Value("${app.reading.openai.model}") String model
	) {
		this.readingGenerator = readingGenerator;
		this.tarotPromptCatalog = tarotPromptCatalog;
		this.sajuPromptCatalog = sajuPromptCatalog;
		this.caseLoader = caseLoader;
		this.tarotInputValidator = tarotInputValidator;
		this.sajuInputValidator = sajuInputValidator;
		this.reportWriter = reportWriter;
		this.labelFactory = labelFactory;
		this.kind = kind;
		this.caseId = caseId;
		this.execute = execute;
		this.model = model;
	}

	void run() {
		AiPreviewCase previewCase = caseLoader.load(kind, caseId);
		String label = labelFactory.create(previewCase.kind());
		if (!SAFE_LABEL.matcher(label).matches()) {
			throw new IllegalArgumentException("Preview label must use letters, digits, hyphens, or underscores");
		}
		PromptIdentity prompt = promptIdentity(previewCase);
		printPlan(previewCase, prompt, label);
		if (!execute) {
			System.out.println("Dry run only. Add -Pexecute=true to call OpenAI.");
			return;
		}

		Instant startedAt = Instant.now();
		long startedNanos = System.nanoTime();
		GeneratedReading generated = readingGenerator.generate(
			previewCase.kind(),
			previewCase.spreadType(),
			previewCase.question(),
			previewCase.input()
		);
		long durationMillis = (System.nanoTime() - startedNanos) / 1_000_000;
		AiPreviewReport report = new AiPreviewReport(
			label,
			previewCase.id(),
			previewCase.kind().value(),
			previewCase.spreadType() == null ? null : previewCase.spreadType().value(),
			model,
			prompt.version(),
			prompt.sha256(),
			startedAt,
			durationMillis,
			generated.payload()
		);
		AiPreviewWrittenReport output = reportWriter.write(report);
		System.out.println("AI preview completed in " + durationMillis + " ms");
		System.out.println("Result JSON: " + output.json());
		System.out.println("Readable Markdown: " + output.markdown());
	}

	private PromptIdentity promptIdentity(AiPreviewCase previewCase) {
		String prompt;
		String version;
		if (previewCase.kind() == ReadingKind.TAROT) {
			List<String> cardIds = tarotInputValidator.validate(
				previewCase.spreadType(), previewCase.input()
			);
			prompt = tarotPromptCatalog.prompt(previewCase.spreadType(), cardIds);
			version = tarotPromptCatalog.version(previewCase.spreadType());
		} else {
			sajuInputValidator.validate(previewCase.input());
			prompt = sajuPromptCatalog.prompt();
			version = sajuPromptCatalog.version();
		}
		return new PromptIdentity(version, sha256(prompt));
	}

	private String sha256(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available", exception);
		}
	}

	private void printPlan(AiPreviewCase previewCase, PromptIdentity prompt, String label) {
		System.out.println("AI preview plan");
		System.out.println("- kind: " + previewCase.kind().value());
		System.out.println("- case: " + previewCase.id());
		System.out.println("- label: " + label);
		System.out.println("- model: " + model);
		System.out.println("- prompt version: " + prompt.version());
		System.out.println("- prompt sha256: " + prompt.sha256());
		System.out.println("- OpenAI calls: " + (execute ? 1 : 0));
	}

	private record PromptIdentity(String version, String sha256) {
	}
}
