package com.jjanpot.server.domain.user.controller.docs;

import com.jjanpot.server.domain.user.dto.request.InviteCodeRequest;
import com.jjanpot.server.domain.user.dto.request.ProfileCreateRequest;
import com.jjanpot.server.domain.user.dto.request.UserAgreementRequest;
import com.jjanpot.server.domain.user.dto.response.InviteCodeResponse;
import com.jjanpot.server.domain.user.dto.response.ProfileCreateResponse;
import com.jjanpot.server.global.common.dto.SuccessResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User - Onboarding", description = "온보딩 API — 호출 순서: ① 약관 동의 → ② 프로필 생성 → ③ 초대코드 참여(팀 참여 시)")
@SecurityRequirement(name = "bearerAuth")
public interface UserControllerDocs {

	@Operation(
		summary = "약관 동의",
		description = """
			온보딩 ① 단계: 필수 약관에 동의합니다.
			
			- `ageVerified`, `termsOfServiceAgreed`, `privacyPolicyAgreed`는 모두 `true` 필수
			- `marketingConsent`는 선택이며, 미입력 시 `false`로 처리
			- 이미 약관 동의를 완료한 사용자의 중복 요청은 성공으로 처리
			"""
	)
	@ApiResponse(responseCode = "200", description = "약관 동의 성공")
	SuccessResponse<Void> agreeToTerms(
		UserAgreementRequest request,
		@Parameter(hidden = true) Long userId
	);

	@Operation(
		summary = "프로필 생성",
		description = """
			온보딩 ② 단계: 사용자 프로필을 생성합니다.
			
			- `nickname` (필수): 최대 10자
			- `birthDate` (선택): yyyy-MM-dd 형식 (예: 2000-01-15), 미입력 시 null
			- `profileImageUrl` (선택): Presigned URL 응답의 `imageUrl`, 미입력 시 기본 이미지

			## 프로필 이미지 등록 흐름
			1. `GET /api/images/v1/presigned-url?directory=profile/&contentType=image/jpeg`로 업로드 URL 발급
			2. 응답의 `uploadUrl`로 S3에 PUT 업로드
			3. 응답의 `imageUrl`을 이 API의 `profileImageUrl`에 담아 전송
			"""
	)
	@ApiResponse(responseCode = "200", description = "프로필 생성 성공")
	SuccessResponse<ProfileCreateResponse> onboardCreateProfile(
		ProfileCreateRequest request,
		@Parameter(hidden = true) Long userId
	);

	@Operation(
		summary = "초대코드 입력",
		description = """
			온보딩 ③ 단계: 초대코드를 입력하여 팀에 참여합니다. (팀 참여 시에만 호출)
			
			- `inviteCode`: 6자리 (혼동 문자 O/0/I/1/L 제외)
			- 대기 중(WAITING) 상태의 챌린지가 있는 팀에만 참여 가능
			- 이미 팀원이거나 정원 초과 시 400 에러
			"""
	)
	@ApiResponse(responseCode = "200", description = "팀 참여 성공")
	SuccessResponse<InviteCodeResponse> inputInviteCode(
		InviteCodeRequest request,
		@Parameter(hidden = true) Long userId
	);
}
