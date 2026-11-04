package com.myeongro.api.aipreview;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

import com.myeongro.api.domain.reading.dto.GeneratedReading;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.service.ReadingGenerator;
import com.myeongro.api.domain.saju.service.SajuPromptCatalog;
import com.myeongro.api.domain.tarot.service.TarotPromptCatalog;

@Component
@Profile("ai-preview")
final class AiPreviewRunner {

	private static final Pattern SAFE_LABEL = Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9_-]*");

	private final ReadingGenerator readingGenerator;
	private final TarotPromptCatalog tarotPromptCatalog;
	private final SajuPromptCatalog sajuPromptCatalog;
	private final AiPreviewCaseLoader caseLoader;
	private final AiPreviewReportWriter reportWriter;
	private final AiPreviewLabelFactory labelFactory;
	private final String kind;
	private final String caseId;
	private final boolean execute;
	private final String model;
	private final com.myeongro.api.domain.saju.annual.OpenAiSajuAnnualGenerator annualGenerator;
	private final com.myeongro.api.domain.saju.annual.SajuAnnualPromptCatalog annualPrompts;
	private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

	AiPreviewRunner(
		ReadingGenerator readingGenerator,
		TarotPromptCatalog tarotPromptCatalog,
		SajuPromptCatalog sajuPromptCatalog,
		AiPreviewCaseLoader caseLoader,
		AiPreviewReportWriter reportWriter,
		AiPreviewLabelFactory labelFactory,
		com.myeongro.api.domain.saju.annual.OpenAiSajuAnnualGenerator annualGenerator,
		com.myeongro.api.domain.saju.annual.SajuAnnualPromptCatalog annualPrompts,
		com.fasterxml.jackson.databind.ObjectMapper objectMapper,
		@Value("${ai-preview.kind:}") String kind,
		@Value("${ai-preview.case:}") String caseId,
		@Value("${ai-preview.execute:false}") boolean execute,
		@Value("${app.reading.openai.model}") String model
	) {
		this.readingGenerator = readingGenerator;
		this.tarotPromptCatalog = tarotPromptCatalog;
		this.sajuPromptCatalog = sajuPromptCatalog;
		this.caseLoader = caseLoader;
		this.reportWriter = reportWriter;
		this.labelFactory = labelFactory;
		this.kind = kind;
		this.caseId = caseId;
		this.execute = execute;
		this.model = model;
		this.annualGenerator = annualGenerator;
		this.annualPrompts = annualPrompts;
		this.objectMapper = objectMapper;
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
		Map<String, Object> metrics = Map.of();
		GeneratedReading generated;
		if (isAnnual(previewCase)) {
			var prepared = annualInput(previewCase);
			com.myeongro.api.domain.saju.annual.OpenAiSajuAnnualGenerator.Evaluation evaluated;
			try {
				evaluated = annualGenerator.generate(prepared);
			} catch (com.myeongro.api.domain.saju.annual.SajuAnnualGenerationException exception) {
				var failure = new AiPreviewReport(label, previewCase.id(), "saju", null, model,
					prompt.version(), prompt.sha256(), startedAt, (System.nanoTime() - startedNanos) / 1_000_000,
					Map.of("resultType", "failed", "title", "연간 리포트 검증 실패"), exception.metrics());
				var written = reportWriter.write(failure);
				System.out.println("Rejected annual output metrics: " + written.json());
				throw exception;
			}
			generated = evaluated.reading();
			var measured = new java.util.LinkedHashMap<>(evaluated.metrics());
			measured.put("calendar", objectMapper.convertValue(prepared.calendar(), Map.class));
			metrics = measured;
		} else {
			generated = readingGenerator.generate(
				previewCase.kind(),
				previewCase.spreadType() == null ? null : previewCase.spreadType().value(),
				previewCase.question(),
				previewCase.input()
			);
		}
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
			generated.payload(), metrics
		);
		AiPreviewWrittenReport output = reportWriter.write(report);
		System.out.println("AI preview completed in " + durationMillis + " ms");
		System.out.println("Result JSON: " + output.json());
		System.out.println("Readable Markdown: " + output.markdown());
	}

	private PromptIdentity promptIdentity(AiPreviewCase previewCase) {
		String prompt;
		String version;
		if (isAnnual(previewCase)) {
			annualInput(previewCase); // Validate input even in a dry run.
			prompt = annualPrompts.prompt(); version = annualPrompts.version();
		} else if (previewCase.kind() == ReadingKind.TAROT) {
			List<String> cardIds = selectedCardIds(previewCase.input());
			prompt = tarotPromptCatalog.prompt(previewCase.spreadType(), cardIds);
			version = tarotPromptCatalog.version(previewCase.spreadType());
		} else {
			prompt = sajuPromptCatalog.prompt();
			version = sajuPromptCatalog.version();
		}
		return new PromptIdentity(version, sha256(prompt));
	}

	private boolean isAnnual(AiPreviewCase previewCase) {
		return previewCase.kind() == ReadingKind.SAJU && "annual_monthly".equals(previewCase.input().get("reportType"));
	}

	private com.myeongro.api.domain.saju.annual.SajuAnnualInput annualInput(AiPreviewCase previewCase) {
		Map<String, Object> input = previewCase.input();
		if (!input.keySet().equals(java.util.Set.of("reportType", "targetYear", "focusArea", "calculationSnapshot"))) {
			throw new IllegalArgumentException("Annual preview requires a frozen snapshot only");
		}
		@SuppressWarnings("unchecked")
		Map<String, Object> snapshot = (Map<String, Object>)input.get("calculationSnapshot");
		if (!(input.get("targetYear") instanceof Integer targetYear)) {
			throw new IllegalArgumentException("Annual preview targetYear must be an integer");
		}
		return com.myeongro.api.domain.saju.annual.SajuAnnualInput.from(previewCase.question(),
			(String)input.get("focusArea"), targetYear, snapshot, objectMapper);
	}

	private List<String> selectedCardIds(Map<String, Object> input) {
		Object cardsValue = input.get("cards");
		if (!(cardsValue instanceof List<?> cards)) {
			throw new IllegalArgumentException("Tarot preview cards are required");
		}
		return cards.stream().map(card -> {
			if (!(card instanceof Map<?, ?> values)
				|| !(values.get("cardId") instanceof String cardId)) {
				throw new IllegalArgumentException("Tarot preview card ID is required");
			}
			return cardId;
		}).toList();
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
