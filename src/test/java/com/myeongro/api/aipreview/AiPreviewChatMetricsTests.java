package com.myeongro.api.aipreview;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class AiPreviewChatMetricsTests {
	@Test void delegatesIdenticalPromptAndResponseAndOnlyCapturesAllowlistedMetadata() {
		var tap = new AiPreviewChatMetrics();
		var provider = mock(ChatModel.class);
		var response = response(100, 20);
		var prompt = new Prompt("synthetic question");
		when(provider.call(prompt)).thenReturn(response);
		var model = (ChatModel)tap.postProcessAfterInitialization(provider, "provider");
		tap.begin();
		assertThat(model.call(prompt)).isSameAs(response);
		verify(provider, times(1)).call(same(prompt));
		assertThat(tap.merge(Map.of("calculationVersion", "test"))).containsExactlyInAnyOrderEntriesOf(Map.of(
			"calculationVersion", "test", "responseModel", "actual-model", "promptTokens", 100,
			"completionTokens", 20, "totalTokens", 120, "finishReason", "STOP"));
		tap.clear();
		assertThat(tap.merge(Map.of())).isEmpty();
	}

	@Test void omittedUsageIsNotZeroAndDoesNotRetainPreviousRun() {
		var tap = new AiPreviewChatMetrics();
		ChatModel provider = p -> new ChatResponse(List.of()); // Spring defaults to EmptyUsage.
		var model = (ChatModel)tap.postProcessAfterInitialization(provider, "provider");
		tap.begin();
		model.call(new Prompt("synthetic"));
		assertThat(tap.merge(Map.of("promptTokens", 0, "responseModel", "configured-fallback"))).isEmpty();
		tap.clear();
	}

	@Test void preservesExplicitZeroAndOmitsUnknownTotalsWhenUsageIsPartial() {
		var tap = new AiPreviewChatMetrics();
		ChatModel provider = p -> response(0, null);
		var model = (ChatModel)tap.postProcessAfterInitialization(provider, "provider");
		tap.begin(); model.call(new Prompt("synthetic"));
		assertThat(tap.merge(Map.of())).containsEntry("promptTokens", 0)
			.doesNotContainKeys("completionTokens", "totalTokens");
		tap.clear();
	}

	@Test void nullResponseAndProviderFailureNeverReusePreviousUsageOrRetry() {
		var tap = new AiPreviewChatMetrics();
		var provider = mock(ChatModel.class);
		var failure = new IllegalStateException("synthetic failure");
		var first = response(10, 4);
		when(provider.call(any(Prompt.class))).thenReturn(first).thenReturn((ChatResponse)null).thenThrow(failure);
		var model = (ChatModel)tap.postProcessAfterInitialization(provider, "provider");
		tap.begin(); model.call(new Prompt("a"));
		assertThat(tap.merge(Map.of())).containsEntry("promptTokens", 10);
		model.call(new Prompt("b")); assertThat(tap.merge(Map.of())).isEmpty();
		assertThatThrownBy(() -> model.call(new Prompt("c"))).isSameAs(failure);
		assertThat(tap.merge(Map.of())).isEmpty(); verify(provider, times(3)).call(any(Prompt.class));
		tap.clear();
	}

	@Test void springInjectsWrappedChatModelWithoutChangingProductionConfiguration() {
		try (var context = new AnnotationConfigApplicationContext()) {
			context.registerBean("metrics", AiPreviewChatMetrics.class);
			context.registerBean("provider", ChatModel.class, () -> p -> response(12, 3));
			context.refresh();
			var tap = context.getBean(AiPreviewChatMetrics.class);
			tap.begin(); context.getBean(ChatModel.class).call(new Prompt("synthetic"));
			assertThat(tap.merge(Map.of())).containsEntry("totalTokens", 15);
			tap.clear();
		}
	}

	static ChatResponse response(Integer input, Integer output) {
		Integer total = input == null || output == null ? null : input + output;
		var usage = new DefaultUsage(input, output, total, new OpenAiApi.Usage(output, input, total));
		return responseWithUsage(usage);
	}

	private static ChatResponse responseWithUsage(Usage usage) {
		return new ChatResponse(List.of(new Generation(new AssistantMessage("SYNTHETIC_BODY"),
			ChatGenerationMetadata.builder().finishReason("STOP").build())),
			ChatResponseMetadata.builder().model("actual-model").usage(usage).build());
	}

	@Test void nativeNullCountersAndAbsentTotalAreNotInventedByDefaultUsage() {
		var tap = new AiPreviewChatMetrics();
		ChatModel provider = mock(ChatModel.class);
		var missing = responseWithUsage(new DefaultUsage(null, null, null, new OpenAiApi.Usage(null, null, null)));
		var missingTotal = responseWithUsage(new DefaultUsage(12, 3, null, new OpenAiApi.Usage(3, 12, null)));
		when(provider.call(any(Prompt.class))).thenReturn(missing).thenReturn(missingTotal);
		var model = (ChatModel)tap.postProcessAfterInitialization(provider, "provider");
		tap.begin(); model.call(new Prompt("missing"));
		assertThat(tap.merge(Map.of())).doesNotContainKeys("promptTokens", "completionTokens", "totalTokens");
		model.call(new Prompt("partial"));
		assertThat(tap.merge(Map.of())).containsEntry("promptTokens", 12).containsEntry("completionTokens", 3)
			.doesNotContainKeys("totalTokens");
		tap.clear();
	}

	@Test void nativeExplicitZeroIsPreservedButUnrecoverableZerosAndTotalsAreOmitted() {
		var tap = new AiPreviewChatMetrics();
		ChatModel provider = mock(ChatModel.class);
		var zero = response(0, 0);
		var noRaw = responseWithUsage(new DefaultUsage(null, null));
		var partialNoRaw = responseWithUsage(new DefaultUsage(12, null));
		when(provider.call(any(Prompt.class))).thenReturn(zero).thenReturn(noRaw).thenReturn(partialNoRaw);
		var model = (ChatModel)tap.postProcessAfterInitialization(provider, "provider");
		tap.begin(); model.call(new Prompt("zero"));
		assertThat(tap.merge(Map.of())).containsEntry("promptTokens", 0).containsEntry("completionTokens", 0).containsEntry("totalTokens", 0);
		model.call(new Prompt("unknown"));
		assertThat(tap.merge(Map.of())).doesNotContainKeys("promptTokens", "completionTokens", "totalTokens");
		model.call(new Prompt("partial unknown"));
		assertThat(tap.merge(Map.of())).containsEntry("promptTokens", 12).doesNotContainKeys("completionTokens", "totalTokens");
		tap.clear();
	}
}
