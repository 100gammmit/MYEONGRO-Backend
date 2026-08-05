package com.myeongro.api.domain.saju.result;

import java.util.List;

public record SajuReadingSection(
	String id,
	String heading,
	String body,
	List<String> evidenceKeys
) {
	public SajuReadingSection {
		evidenceKeys = evidenceKeys == null ? null : List.copyOf(evidenceKeys);
	}
}
