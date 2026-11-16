package com.myeongro.api.domain.saju.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import com.myeongro.api.domain.saju.calculation.ManseryeokChart;
import com.myeongro.api.domain.saju.service.ManseryeokCalculationService;
import com.myeongro.api.domain.saju.place.SajuBirthPlaceCatalog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;

@RestController
@RequestMapping("/api/saju/calculations")
@Tag(name="Public Manseryeok",description="무상태 무료 만세력 계산. 기능은 기본 비활성입니다.")
public class SajuCalculationController {
	private final ManseryeokCalculationService service;
	private final SajuBirthPlaceCatalog catalog;
	public SajuCalculationController(ManseryeokCalculationService service, SajuBirthPlaceCatalog catalog) { this.service=service; this.catalog=catalog; }
	@PostMapping
	@Operation(summary="무료 만세력 계산",description="계산만 수행합니다. 인증·동의·크레딧·기록 저장·AI 호출을 사용하지 않습니다.")
	public ResponseEntity<ManseryeokChart> calculate(@RequestBody SajuCalculationRequest request) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.calculate(request));
	}
	@GetMapping("/birth-places")
	@Operation(summary="공개 시·도/시·군·구 선택 목록")
	public ResponseEntity<Map<String,Object>> places() {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("version",catalog.publicVersion(),"provinces",catalog.publicProvinces()));
	}
}
