package com.myeongro.api.aipreview;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.reading.service.DirectIdentifierInputGuard;
import com.myeongro.api.domain.saju.controller.SajuReadingCreateRequest;
import com.myeongro.api.domain.saju.service.SajuReadingInputAssembler;
import com.myeongro.api.domain.saju.service.SajuReadingInputNormalizer;

/** Uses synthetic birth profiles locally; only the production-calculated payload reaches AI. */
@Component
@Profile("ai-preview")
final class AiPreviewSajuInputPreparer {
	private static final Set<String> INPUT_KEYS = Set.of("birthProfile", "focusArea", "targetYear", "reportType");
	private final ObjectMapper mapper;
	private final SajuReadingInputNormalizer normalizer;
	private final SajuReadingInputAssembler assembler;
	private final DirectIdentifierInputGuard identifierGuard;

	AiPreviewSajuInputPreparer(ObjectMapper mapper, SajuReadingInputNormalizer normalizer,
		SajuReadingInputAssembler assembler, DirectIdentifierInputGuard identifierGuard) {
		this.mapper = mapper;
		this.normalizer = normalizer;
		this.assembler = assembler;
		this.identifierGuard = identifierGuard;
	}

	Prepared prepare(AiPreviewCase source) {
		if (!INPUT_KEYS.containsAll(source.input().keySet()) || !source.input().containsKey("birthProfile")) {
			throw new IllegalArgumentException("Saju preview requires birthProfile, not calculation or calculationSnapshot");
		}
		if (!(source.input().get("targetYear") instanceof Integer)) {
			throw new IllegalArgumentException("Saju preview targetYear must be an explicit integer");
		}
		var requestValues = new LinkedHashMap<String, Object>(source.input());
		requestValues.put("question", source.question());
		// Required HTTP identifier is irrelevant to this non-persistent local calculation.
		requestValues.put("requestId", new UUID(0, 0).toString());
		SajuReadingCreateRequest request;
		try {
			request = mapper.convertValue(requestValues, SajuReadingCreateRequest.class);
		} catch (IllegalArgumentException exception) {
			// Jackson conversion errors can include the original birth-profile values.
			throw new IllegalArgumentException("Invalid Saju preview birth request");
		}
		var normalized = normalizer.normalize(request);
		identifierGuard.validate(normalized);
		var prepared = assembler.assemble(normalized, normalized.targetYear(), () -> {});
		var calculation = (Map<?, ?>) prepared.storedPayload().get("calculationSnapshot");
		var metadata = new LinkedHashMap<String, Object>();
		metadata.put("inputSource", "birth_profile");
		for (String key : java.util.List.of("calculationVersion", "engine", "engineVersion", "cityCatalogVersion", "targetYear")) {
			if (calculation.get(key) != null) metadata.put(key, calculation.get(key));
		}
		return new Prepared(new AiPreviewCase(source.id(), source.kind(), null,
			prepared.question(), prepared.aiPayload()), Map.copyOf(metadata));
	}

	record Prepared(AiPreviewCase previewCase, Map<String, Object> calculationMetadata) { }
}
