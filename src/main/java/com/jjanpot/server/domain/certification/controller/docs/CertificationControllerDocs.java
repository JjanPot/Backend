package com.jjanpot.server.domain.certification.controller.docs;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.jjanpot.server.domain.certification.dto.request.CreateCertificationRequest;
import com.jjanpot.server.domain.certification.dto.response.CertificationFeedResponse;
import com.jjanpot.server.domain.certification.dto.response.CreateCertificationResponse;
import com.jjanpot.server.domain.certification.dto.response.ToggleLikeResponse;
import com.jjanpot.server.global.common.dto.SuccessResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Certification", description = "인증 API")
@SecurityRequirement(name = "bearerAuth")
public interface CertificationControllerDocs {

	@Operation(
		summary = "인증 생성",
		description = """
			절약 인증을 생성합니다. (multipart/form-data)

			## 요청 형식
			Content-Type: `multipart/form-data`로 아래 두 파트를 전송합니다.
			- `request` (필수): JSON 문자열, Content-Type을 `application/json`으로 설정
			- `image` (선택): 이미지 파일 (JPEG, PNG, WEBP / 최대 10MB)

			## request JSON 예시
			```json
			{
			  "challengeId": 1,
			  "spendType": "SPEND",
			  "categoryId": 1,
			  "spentAmount": 3500,
			  "memo": "텀블러에 담아서 먹음",
			  "spentAt": "2026-03-29T10:30:00"
			}
			```

			## 비즈니스 규칙
			- 진행 중(ONGOING) 상태의 챌린지에서만 인증 가능
			- 하루 최대 3회 (spentAt 날짜 기준, Asia/Seoul)
			- 챌린지에 설정된 카테고리만 선택 가능
			- 지출(SPEND): spentAmount 필수, 절약 금액 = 기준 금액 - 소비 금액 (음수 가능)
			- 무지출(NO_SPEND): spentAmount 불필요, 절약 금액 = 기준 금액 전액
			"""
	)
	@ApiResponse(responseCode = "201", description = "인증 생성 성공")
	SuccessResponse<CreateCertificationResponse> createCertification(
		@Parameter(hidden = true) Long userId,
		CreateCertificationRequest request,
		@Parameter(description = "인증 이미지 (선택, 최대 10MB, JPEG/PNG/WEBP)") MultipartFile image
	);

	@Operation(
		summary = "인증 수정",
		description = """
			본인의 절약 인증을 수정합니다. (multipart/form-data)

			## 요청 형식
			인증 생성과 동일한 multipart/form-data 형식입니다.
			- `request` (필수): JSON 문자열, Content-Type을 `application/json`으로 설정
			- `image` (선택): 새 이미지 파일 (JPEG, PNG, WEBP / 최대 10MB)
			- `deleteImage` (선택): 기존 이미지만 삭제하려면 `true`

			## 이미지 처리
			- 새 이미지 첨부 시: 새 이미지 업로드 → 기존 S3 이미지는 커밋 후 삭제
			- 이미지 미첨부 + `deleteImage=true`: 기존 이미지 URL 제거 → 기존 S3 이미지는 커밋 후 삭제
			- 이미지 미첨부 + `deleteImage` 미전송/false: 기존 이미지 URL 유지

			## 비즈니스 규칙
			- 진행 중(ONGOING) 상태의 챌린지에서만 수정 가능
			- 본인의 인증만 수정 가능
			- 수정 시 주차 절약 금액이 자동 보정됩니다 (기존 절약 금액 차감 → 새 절약 금액 누적)
			"""
	)
	@ApiResponse(responseCode = "200", description = "인증 수정 성공")
	SuccessResponse<CreateCertificationResponse> updateCertification(
		@Parameter(hidden = true) Long userId,
		@Parameter(description = "인증 ID") Long certificationId,
		CreateCertificationRequest request,
		@Parameter(description = "새 인증 이미지 (선택, 최대 10MB, JPEG/PNG/WEBP)") MultipartFile image,
		@Parameter(description = "기존 이미지만 삭제하려면 true") Boolean deleteImage
	);

	@Operation(
		summary = "인증 삭제",
		description = """
			본인의 절약 인증을 삭제합니다.

			- 진행 중(ONGOING) 상태의 챌린지에서만 삭제 가능합니다.
			- 본인의 인증만 삭제할 수 있습니다.
			- 삭제 시 주차 절약 금액에서 해당 인증의 절약 금액이 차감됩니다.
			- 연관된 좋아요와 S3 이미지도 함께 삭제됩니다.
			"""
	)
	@ApiResponse(responseCode = "204", description = "인증 삭제 성공")
	SuccessResponse<Void> deleteCertification(
		@Parameter(hidden = true) Long userId,
		@Parameter(description = "인증 ID") Long certificationId
	);

	@Operation(
		summary = "챌린지의 인증 목록 조회",
		description = """
			특정 챌린지의 인증 피드를 최신순으로 목록 조회합니다.

			- 해당 챌린지 팀원만 조회할 수 있습니다.
			- savedAmount가 양수(+)이면 절약 성공, 음수(-)이면 기준 초과 소비입니다.
			- 내가 차단한 유저의 게시글은 자동으로 제외됩니다.
			- 신고되어 비노출(숨김) 처리된 게시글은 모든 유저에게 제외됩니다.
			- isMe 필드로 본인 게시글 여부를 구분합니다.
			- userId: 작성자 유저 ID (사용자 신고/차단 시 reportedUserId/blockedUserId로 활용)
			"""
	)
	@ApiResponse(responseCode = "200", description = "인증 피드 조회 성공")
	SuccessResponse<List<CertificationFeedResponse>> getCertificationFeed(
		@Parameter(hidden = true) Long userId,
		@Parameter(description = "챌린지 ID") Long challengeId
	);

	@Operation(
		summary = "인증 좋아요 토글",
		description = """
			인증 게시글에 좋아요를 토글합니다.

			- 좋아요가 없으면 추가, 있으면 취소합니다.
			- 본인 게시글에도 좋아요 가능합니다.
			"""
	)
	@ApiResponse(responseCode = "200", description = "좋아요 토글 성공")
	SuccessResponse<ToggleLikeResponse> toggleLike(
		@Parameter(hidden = true) Long userId,
		@Parameter(description = "인증 ID") Long certificationId
	);
}
