package com.jjanpot.server.domain.auth.dto.response;

import com.jjanpot.server.domain.auth.dto.LoginUserInfo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponse {
	private String accessToken;
	private String refreshToken;
	private LoginUserInfo user;

	@Schema(description = "true이면 온보딩 미완료 → 온보딩 화면으로 이동")
	private boolean newUser;

	@Schema(description = "약관 동의 완료 여부")
	private boolean termsAgreed;

	@Schema(description = "온보딩 완료 여부")
	private boolean onboardingCompleted;

	@Schema(description = "다음 온보딩 단계 (AGREEMENT, PROFILE, COMPLETED)")
	private String nextOnboardingStep;

	@Schema(description = "true이면 앱 심사 계정 → 심사용 버튼(즉시 시작/종료) 노출. 카카오 jjanpot0220@gmail.com 또는 구글 jjanpod.swyp4@gmail.com 계정만 true")
	private boolean reviewMode;

	public static LoginResponse of(
		String accessToken,
		String refreshToken,
		LoginUserInfo user,
		boolean newUser,
		boolean termsAgreed,
		boolean onboardingCompleted,
		boolean reviewMode
	) {
		return LoginResponse.builder()
			.accessToken(accessToken)
			.refreshToken(refreshToken)
			.user(user)
			.newUser(newUser)
			.termsAgreed(termsAgreed)
			.onboardingCompleted(onboardingCompleted)
			.nextOnboardingStep(resolveNextOnboardingStep(termsAgreed, onboardingCompleted))
			.reviewMode(reviewMode)
			.build();
	}

	private static String resolveNextOnboardingStep(boolean termsAgreed, boolean onboardingCompleted) {
		if (onboardingCompleted) {
			return "COMPLETED";
		}
		if (termsAgreed) {
			return "PROFILE";
		}
		return "AGREEMENT";
	}
}
