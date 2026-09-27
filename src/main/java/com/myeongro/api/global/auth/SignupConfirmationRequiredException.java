package com.myeongro.api.global.auth;

public class SignupConfirmationRequiredException extends RuntimeException {

	public SignupConfirmationRequiredException() {
		super("성인 여부와 서비스 이용약관 동의를 모두 확인해 주세요.");
	}
}
