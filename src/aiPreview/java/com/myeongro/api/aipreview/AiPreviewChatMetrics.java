package com.myeongro.api.aipreview;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.config.BeanPostProcessor;
import reactor.core.publisher.Flux;

/** Preview-only metadata tap: delegates the original request and response unchanged. */
final class AiPreviewChatMetrics implements BeanPostProcessor {

	static final List<String> KEYS = List.of("responseModel", "promptTokens", "completionTokens", "totalTokens", "finishReason");
	private final ThreadLocal<Map<String, Object>> current = new ThreadLocal<>();

	void begin() { current.set(new LinkedHashMap<>()); }
	void clear() { current.remove(); }

	Map<String, Object> merge(Map<String, Object> existing) {
		var result = new LinkedHashMap<>(existing);
		KEYS.forEach(result::remove);
		if (current.get() != null) result.putAll(current.get());
		return result;
	}

	private void capture(ChatResponse response) {
		var metrics = current.get();
		if (metrics == null) return;
		metrics.clear();
		if (response == null) return;
		var metadata = response.getMetadata();
		if (metadata != null) {
			if (metadata.getModel() != null && !metadata.getModel().isBlank()) metrics.put("responseModel", metadata.getModel());
			var usage = metadata.getUsage();
			if (usage != null && !(usage instanceof EmptyUsage)) {
				// DefaultUsage replaces nulls with zero and calculates missing totals.
				// Read only the provider's three nullable counters; never retain native usage.
				if (usage.getNativeUsage() instanceof OpenAiApi.Usage original) {
					if (original.promptTokens() != null) metrics.put("promptTokens", original.promptTokens());
					if (original.completionTokens() != null) metrics.put("completionTokens", original.completionTokens());
					if (original.totalTokens() != null) metrics.put("totalTokens", original.totalTokens());
				} else {
					// Without raw presence information, zero and calculated totals are ambiguous.
					if (usage.getPromptTokens() != null && usage.getPromptTokens() > 0) metrics.put("promptTokens", usage.getPromptTokens());
					if (usage.getCompletionTokens() != null && usage.getCompletionTokens() > 0) metrics.put("completionTokens", usage.getCompletionTokens());
				}
			}
		}
		if (response.getResult() != null && response.getResult().getMetadata() != null) {
			String reason = response.getResult().getMetadata().getFinishReason();
			if (reason != null && !reason.isBlank()) metrics.put("finishReason", reason);
		}
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) {
		if (!(bean instanceof ChatModel delegate)) return bean;
		return new ChatModel() {
			@Override public ChatResponse call(Prompt prompt) {
				if (current.get() != null) current.get().clear();
				ChatResponse response = delegate.call(prompt);
				capture(response);
				return response;
			}
			@Override public ChatOptions getDefaultOptions() { return delegate.getDefaultOptions(); }
			@Override public Flux<ChatResponse> stream(Prompt prompt) { return delegate.stream(prompt); }
		};
	}
}
