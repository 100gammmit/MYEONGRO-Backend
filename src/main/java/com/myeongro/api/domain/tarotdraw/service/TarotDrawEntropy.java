package com.myeongro.api.domain.tarotdraw.service;

import java.util.List;

public interface TarotDrawEntropy {

	String sessionId();

	String candidateToken();

	List<String> shuffledCopy(List<String> cards);
}
