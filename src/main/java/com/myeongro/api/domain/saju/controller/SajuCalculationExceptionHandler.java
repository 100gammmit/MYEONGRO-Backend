package com.myeongro.api.domain.saju.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import com.myeongro.api.domain.reading.exception.InvalidReadingRequestException;
import com.myeongro.api.domain.saju.service.ManseryeokCalculationService;

@RestControllerAdvice(assignableTypes=SajuCalculationController.class)
public class SajuCalculationExceptionHandler {
	@ExceptionHandler(InvalidReadingRequestException.class)
	ResponseEntity<Map<String,String>> invalid(InvalidReadingRequestException e) {
		return ResponseEntity.badRequest().header("Cache-Control","no-store").body(Map.of("code",e.getCode(),"field",e.getField()==null ? "request" : e.getField(),"error",e.getMessage()));
	}
	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<Map<String,String>> malformed(HttpMessageNotReadableException e) {
		// Do not log or return Jackson's message/cause, which may retain raw body excerpts.
		return ResponseEntity.badRequest().header("Cache-Control","no-store").body(Map.of("code","INVALID_CALCULATION_REQUEST","field","request","error","계산 요청 형식을 확인해 주세요."));
	}
	@ExceptionHandler(ManseryeokCalculationService.Unavailable.class)
	ResponseEntity<Map<String,String>> unavailable(ManseryeokCalculationService.Unavailable e) {
		SajuPublicCalculationFilter.logRejection(e.code());
		return ResponseEntity.status(503).header("Cache-Control","no-store").body(Map.of("code",e.code(),"error",e.getMessage()));
	}
	@ExceptionHandler(ManseryeokCalculationService.Failed.class)
	ResponseEntity<Map<String,String>> failed(ManseryeokCalculationService.Failed e) {
		return ResponseEntity.internalServerError().header("Cache-Control","no-store").body(Map.of("code","SAJU_CALCULATION_FAILED","error",e.getMessage()));
	}
}
