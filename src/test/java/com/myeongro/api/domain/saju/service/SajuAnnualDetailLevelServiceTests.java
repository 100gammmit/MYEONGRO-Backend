package com.myeongro.api.domain.saju.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.consent.entity.ConsentScope;
import com.myeongro.api.domain.reading.exception.InvalidReadingRequestException;
import com.myeongro.api.domain.reading.exception.RequiredConsentMissingException;
import com.myeongro.api.domain.reading.service.ReadingCreationWorkflow;
import com.myeongro.api.domain.readingcredit.ReadingCreditTestFixtures;
import com.myeongro.api.domain.saju.calculation.ApproximateBirthTimeResolver;
import com.myeongro.api.domain.saju.calculation.LunarJavaFourPillarsAdapter;
import com.myeongro.api.domain.saju.calculation.SajuCalculationService;
import com.myeongro.api.domain.saju.calculation.TrueSolarTimeCorrector;
import com.myeongro.api.domain.saju.controller.SajuDetailLevelRequest;
import com.myeongro.api.domain.saju.model.SajuBirthProfileRequest;
import com.myeongro.api.domain.saju.place.SajuBirthPlaceCatalog;

class SajuAnnualDetailLevelServiceTests {

	private static final UUID USER_ID = UUID.fromString("3b413be2-2b81-4802-8c6a-f868a85d8d83");
	private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

	private final ReadingCreationWorkflow workflow = mock(ReadingCreationWorkflow.class);
	private final SajuCalculationService calculator = calculator();
	private final SajuAnnualDetailLevelService service = new SajuAnnualDetailLevelService(
		workflow, new SajuReadingInputNormalizer(places()), calculator, ReadingCreditTestFixtures.properties(),
		Clock.fixed(Instant.parse("2026-11-23T03:00:00Z"), ZoneOffset.UTC)
	);

	@Test
	void anExactTimeGetsTheFullReportAtItsPrice() {
		var result = service.check(USER_ID, request(new SajuBirthProfileRequest(
			"solar", "1992-08-17", "12:00", "exact", "11", "male")));
		assertThat(result).isEqualTo(new SajuAnnualDetailLevelService.Result("full", 6));
	}

	@Test
	void anUnknownTimeGetsTheBriefReportAtItsPrice() {
		var result = service.check(USER_ID, request(new SajuBirthProfileRequest(
			"solar", "1992-08-17", null, "unknown", null, "unspecified")));
		assertThat(result).isEqualTo(new SajuAnnualDetailLevelService.Result("brief", 4));
	}

	@Test
	void anApproximateTimeAcrossMidnightGetsTheBriefReport() {
		var result = service.check(USER_ID, request(new SajuBirthProfileRequest(
			"solar", "1992-08-17", "00:10", "approximate", "11", "female")));
		assertThat(result.detailLevel()).isEqualTo("brief");
	}

	@Test
	void validatesTheBirthInputLikeAReading() {
		assertThatThrownBy(() -> service.check(USER_ID, request(new SajuBirthProfileRequest(
			"solar", "1992-08-17", "12:00", "unknown", null, "unspecified"))))
			.isInstanceOf(InvalidReadingRequestException.class);
	}

	@Test
	void requiresTheSajuConsentBeforeCalculating() {
		doThrow(new RequiredConsentMissingException("필수 동의가 필요합니다."))
			.when(workflow).requireConsent(USER_ID, ConsentScope.SAJU);
		SajuCalculationService guarded = mock(SajuCalculationService.class);
		var gated = new SajuAnnualDetailLevelService(workflow, new SajuReadingInputNormalizer(places()), guarded,
			ReadingCreditTestFixtures.properties());
		assertThatThrownBy(() -> gated.check(USER_ID, request(new SajuBirthProfileRequest(
			"solar", "1992-08-17", null, "unknown", null, "unspecified"))))
			.isInstanceOf(RequiredConsentMissingException.class);
		verifyNoInteractions(guarded);
	}

	private static SajuDetailLevelRequest request(SajuBirthProfileRequest profile) {
		return new SajuDetailLevelRequest(profile, null);
	}

	private static SajuBirthPlaceCatalog places() {
		return new SajuBirthPlaceCatalog(MAPPER, new ClassPathResource("saju/birth-places/kr-admin-v1.json"));
	}

	private static SajuCalculationService calculator() {
		var corrector = new TrueSolarTimeCorrector();
		return new SajuCalculationService(places(), corrector,
			new ApproximateBirthTimeResolver(corrector, new LunarJavaFourPillarsAdapter()));
	}
}
