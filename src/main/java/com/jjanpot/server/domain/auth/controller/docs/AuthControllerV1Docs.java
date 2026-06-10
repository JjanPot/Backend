package com.jjanpot.server.domain.auth.controller.docs;

import org.springframework.web.bind.annotation.PathVariable;

import com.jjanpot.server.domain.auth.dto.request.LoginRequest;
import com.jjanpot.server.domain.auth.dto.request.RefreshRequest;
import com.jjanpot.server.domain.auth.dto.response.LoginResponse;
import com.jjanpot.server.domain.auth.dto.response.RefreshResponse;
import com.jjanpot.server.global.common.dto.SuccessResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Auth", description = "소셜 로그인 / 인증 API")
public interface AuthControllerV1Docs {

	@Operation(
		summary = "소셜 로그인",
		description = """
			소셜 로그인 API 입니다.

			**지원 플랫폼**
			- kakao
			- google
			- apple

			클라이언트에서 받은 accessToken을 전달하면
			서버에서 사용자 정보를 조회하고 로그인 처리 후 JWT 토큰을 발급합니다.

			---

			**응답 필드 설명**

			| 필드 | 설명 |
			|---|---|
			| `accessToken` | API 인증에 사용하는 JWT |
			| `refreshToken` | 액세스 토큰 재발급용 토큰 |
			| `user.userId` | 유저 고유 ID |
			| `user.nickname` | 유저 닉네임 |
			| `newUser` | `true`이면 온보딩 미완료 → 온보딩 화면으로 이동 |
			| `termsAgreed` | 약관 동의 완료 여부 |
			| `onboardingCompleted` | 온보딩 완료 여부 |
			| `nextOnboardingStep` | 다음 온보딩 단계 (`AGREEMENT`, `PROFILE`, `COMPLETED`) |
			| `reviewMode` | `true`이면 앱 심사 계정 → 심사용 버튼 노출 |

			---

			**reviewMode 상세**

			심사 계정으로 로그인하면 `reviewMode: true`가 반환됩니다.
			앱은 이 값을 보고 심사 전용 버튼(챌린지 즉시 시작 / 즉시 종료)의 노출 여부를 결정합니다.

			심사 계정 목록:
			- 카카오 `jjanpot0220@gmail.com`
			- 구글 `jjanpod.swyp4@gmail.com`

			심사 버튼 연결 API:
			- `POST /api/auth/v1/review/challenge/{id}/start` — 즉시 시작 (리뷰용 참가자/인증 포스트 자동 생성 포함)
			- `POST /api/auth/v1/review/challenge/{id}/finish` — 즉시 종료 + 결과 생성
			"""
	)
	@ApiResponse(responseCode = "200", description = "로그인 성공")
	SuccessResponse<LoginResponse> login(

		@Parameter(
			description = "소셜 로그인 플랫폼 (kakao)",
			example = "kakao"
		)
		@PathVariable String provider,

		@Parameter(description = "소셜 accessToken")
		@RequestBody LoginRequest request
	);

	@Operation(
		summary = "토큰 재발급",
		description = """
			refresh token을 사용하여 access token을 재발급하는 API 입니다.
			
			refresh token의 만료가 임박한 경우
			새로운 refresh token도 함께 재발급합니다.
			"""
	)
	@ApiResponse(responseCode = "200", description = "토큰 재발급 성공")
	SuccessResponse<RefreshResponse> refresh(

		@Parameter(description = "refresh token")
		@RequestBody RefreshRequest request
	);

	@Operation(
		summary = "로그아웃",
		description = """
			로그아웃 API 입니다.

			서버에 저장된 refresh token을 삭제하여
			이후 토큰 재발급을 차단합니다.
			"""
	)
	@ApiResponse(responseCode = "200", description = "로그아웃 성공")
	SuccessResponse<Void> logout(Long userId);
}
