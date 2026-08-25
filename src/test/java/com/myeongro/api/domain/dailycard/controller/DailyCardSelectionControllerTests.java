package com.myeongro.api.domain.dailycard.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.myeongro.api.domain.dailycard.exception.DailyCardContentVersionMismatchException;
import com.myeongro.api.domain.dailycard.service.DailyCardSelectionService;

class DailyCardSelectionControllerTests {

	private static final UUID DRAW_ID = UUID.fromString(
		"82ed11d5-2269-438c-9815-42e6f13735f4"
	);
	private DailyCardSelectionService service;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		service = org.mockito.Mockito.mock(DailyCardSelectionService.class);
		mockMvc = MockMvcBuilders.standaloneSetup(new DailyCardSelectionController(service))
			.setControllerAdvice(new DailyCardSelectionExceptionHandler())
			.build();
	}

	@Test
	void returnsSelectionWithoutAuthenticationContract() throws Exception {
		when(service.select(DRAW_ID, 3, "daily-one-card-static-v1"))
			.thenReturn(new DailyCardSelectionResponse(new DailyCardSelectionResponse.Selection(
				LocalDate.of(2026, 8, 25), "major-17-star", 3,
				"daily-one-card-static-v1"
			)));

		mockMvc.perform(post("/api/tarot/daily-card-selections")
				.contentType(MediaType.APPLICATION_JSON)
				.content(validRequest()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.selection.dateKst").value("2026-08-25"))
			.andExpect(jsonPath("$.selection.cardId").value("major-17-star"))
			.andExpect(jsonPath("$.selection.variantIndex").value(3));
	}

	@Test
	void rejectsUnknownAndMalformedFields() throws Exception {
		mockMvc.perform(post("/api/tarot/daily-card-selections")
				.contentType(MediaType.APPLICATION_JSON)
				.content(validRequest().replace("}", ",\"userId\":\"" + DRAW_ID + "\"}")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"))
			.andExpect(jsonPath("$.field").value("userId"));

		mockMvc.perform(post("/api/tarot/daily-card-selections")
				.contentType(MediaType.APPLICATION_JSON)
				.content(validRequest().replace("\"selectedSlot\":3", "\"selectedSlot\":6")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.field").value("selectedSlot"));
	}

	@Test
	void reportsContentVersionMismatchAsConflict() throws Exception {
		when(service.select(DRAW_ID, 3, "daily-one-card-static-v1"))
			.thenThrow(new DailyCardContentVersionMismatchException());

		mockMvc.perform(post("/api/tarot/daily-card-selections")
				.contentType(MediaType.APPLICATION_JSON)
				.content(validRequest()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DAILY_CARD_CONTENT_VERSION_MISMATCH"));
	}

	private String validRequest() {
		return """
			{
			  "drawId":"82ed11d5-2269-438c-9815-42e6f13735f4",
			  "selectedSlot":3,
			  "contentVersion":"daily-one-card-static-v1"
			}
			""";
	}
}
