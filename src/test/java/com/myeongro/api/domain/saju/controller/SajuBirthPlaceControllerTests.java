package com.myeongro.api.domain.saju.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.myeongro.api.domain.consent.dto.ConsentStatus;
import com.myeongro.api.domain.consent.entity.ConsentDocumentType;
import com.myeongro.api.domain.consent.service.ConsentService;
import com.myeongro.api.domain.saju.place.SajuBirthPlaceCatalog;
import com.myeongro.api.global.auth.AuthenticatedUser;
import com.myeongro.api.global.auth.AuthenticatedUserResolver;

class SajuBirthPlaceControllerTests {

	private static final UUID USER_ID = UUID.fromString(
		"3b413be2-2b81-4802-8c6a-f868a85d8d83"
	);

	private SajuBirthPlaceCatalog catalog;
	private ConsentService consentService;
	private AuthenticatedUserResolver userResolver;
	private TestingAuthenticationToken authentication;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		catalog = org.mockito.Mockito.mock(SajuBirthPlaceCatalog.class);
		consentService = org.mockito.Mockito.mock(ConsentService.class);
		userResolver = org.mockito.Mockito.mock(AuthenticatedUserResolver.class);
		authentication = new TestingAuthenticationToken("user", null, "ROLE_USER");
		when(userResolver.requireUser(authentication)).thenReturn(new AuthenticatedUser(USER_ID));
		mockMvc = MockMvcBuilders.standaloneSetup(new SajuBirthPlaceController(
			catalog, consentService, userResolver
		)).build();
	}

	@Test
	void returnsVersionedProvinceCatalogAfterRequiredConsent() throws Exception {
		when(consentService.getUserStatus(USER_ID)).thenReturn(new ConsentStatus(
			ConsentDocumentType.required(), ConsentDocumentType.required(), true
		));
		when(catalog.version()).thenReturn("kr-admin-v1-province");
		when(catalog.provinces()).thenReturn(List.of(
			new SajuBirthPlaceCatalog.ProvinceView("11", "서울특별시")
		));

		mockMvc.perform(get("/api/saju/birth-places").principal(authentication))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version").value("kr-admin-v1-province"))
			.andExpect(jsonPath("$.provinces[0].provinceCode").value("11"))
			.andExpect(jsonPath("$.provinces[0].provinceName").value("서울특별시"))
			.andExpect(jsonPath("$.provinces[0].cities").doesNotExist());
	}

	@Test
	void rejectsCatalogAccessBeforeRequiredConsent() throws Exception {
		when(consentService.getUserStatus(USER_ID)).thenReturn(new ConsentStatus(
			List.of(), ConsentDocumentType.required(), false
		));

		mockMvc.perform(get("/api/saju/birth-places").principal(authentication))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("필수 동의가 필요합니다."));
	}
}
