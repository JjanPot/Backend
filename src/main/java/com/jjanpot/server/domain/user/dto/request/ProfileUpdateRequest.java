package com.jjanpot.server.domain.user.dto.request;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "프로필 수정 요청")
public record ProfileUpdateRequest(

	@Schema(description = "닉네임 (미입력 시 기존 값 유지)", example = "절약왕", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
	@Size(max = 10, message = "닉네임은 10자 이하여야 합니다.")
	String nickname,

	@Schema(description = "생년월일 (미입력 시 기존 값 유지)", example = "2000-01-15", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
	LocalDate birthDate,

	@Schema(description = "프로필 이미지 URL (Presigned URL 응답의 imageUrl 전달, null이면 기존 값 유지, 빈 문자열은 허용하지 않음)", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
	String profileImageUrl,

	@Schema(description = "true이면 프로필 이미지를 기본 이미지로 변경", example = "false", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
	Boolean resetProfileImage
) {
}
