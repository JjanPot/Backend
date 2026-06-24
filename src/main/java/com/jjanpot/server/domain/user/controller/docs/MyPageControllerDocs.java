package com.jjanpot.server.domain.user.controller.docs;

import com.jjanpot.server.domain.user.dto.request.ProfileUpdateRequest;
import com.jjanpot.server.domain.user.dto.response.ChallengeStatsResponse;
import com.jjanpot.server.domain.user.dto.response.UserProfileResponse;
import com.jjanpot.server.global.common.dto.SuccessResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User - MyPage", description = "마이페이지 API")
@SecurityRequirement(name = "JWT TOKEN")
public interface MyPageControllerDocs {

	@Operation(summary = "챌린지 통계 조회", description = "로그인한 사용자의 총 챌린지 수, 성공/실패 수, 성공률을 조회합니다.")
	@ApiResponse(responseCode = "200", description = "챌린지 통계 조회 성공")
	SuccessResponse<ChallengeStatsResponse> getChallengeStats(@Parameter(hidden = true) Long userId);

	@Operation(summary = "프로필 조회", description = "로그인한 사용자의 프로필 정보를 조회합니다.")
	@ApiResponse(responseCode = "200", description = "프로필 조회 성공")
	SuccessResponse<UserProfileResponse> getProfile(@Parameter(hidden = true) Long userId);

	@Operation(
		summary = "프로필 수정",
		description = """
			로그인한 사용자의 프로필 정보를 수정합니다.

			- `nickname`: 최대 10자, 미입력 시 기존 값 유지
			- `birthDate`: yyyy-MM-dd 형식, 미입력 시 기존 값 유지
			- `profileImageUrl`: Presigned URL로 업로드한 이미지 URL, 미입력/null 시 기존 값 유지, 빈 문자열은 허용하지 않음
			- `resetProfileImage`: `true`이면 프로필 이미지를 기본 이미지로 변경

			## 프로필 이미지 수정 흐름
			1. `GET /api/images/v1/presigned-url?directory=profile/&contentType=image/jpeg`로 업로드 URL 발급
			2. 응답의 `uploadUrl`로 S3에 PUT 업로드
			3. 응답의 `imageUrl`을 이 API의 `profileImageUrl`에 담아 전송

			## 프로필 이미지 삭제 흐름
			- `resetProfileImage=true`로 요청하면 서버가 기본 프로필 이미지 URL로 변경합니다.
			- `resetProfileImage=true`와 `profileImageUrl`이 함께 오면 기본 이미지 변경이 우선 적용됩니다.
			"""
	)
	@ApiResponse(responseCode = "200", description = "프로필 수정 성공")
	SuccessResponse<UserProfileResponse> updateProfile(
		@Parameter(hidden = true) Long userId,
		ProfileUpdateRequest request
	);

	@Operation(
		summary = "회원 탈퇴",
		description = """
			회원 탈퇴 API입니다.

			- 진행 중(WAITING/ONGOING)인 챌린지가 있으면 탈퇴 불가 (400 에러)
			- 탈퇴 시 유저와 연관된 모든 데이터가 영구 삭제됩니다:
			  인증, 좋아요, 챌린지 결과, 알림, 팀 멤버십, 디바이스, 약관 동의, 토큰
			- 삭제된 계정은 복구할 수 없습니다.
			"""
	)
	@ApiResponse(responseCode = "200", description = "회원 탈퇴 성공")
	SuccessResponse<Void> withdraw(@Parameter(hidden = true) Long userId);
}
