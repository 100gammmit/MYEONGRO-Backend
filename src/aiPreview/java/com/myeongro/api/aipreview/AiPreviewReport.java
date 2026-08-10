package com.myeongro.api.aipreview;

import java.time.Instant;
import java.util.Map;

record AiPreviewReport(
	String label,
	String caseId,
	String kind,
	String spreadType,
	String model,
	String promptVersion,
	String promptSha256,
	Instant executedAt,
	long durationMillis,
	Map<String, Object> result
) {
}
