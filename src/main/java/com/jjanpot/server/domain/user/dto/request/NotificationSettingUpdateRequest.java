package com.jjanpot.server.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "알림 설정 수정 요청")
public record NotificationSettingUpdateRequest(

	@NotNull
	@Schema(description = "1일 1회 미인증 알림 활성화 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
	Boolean dailyEnabled,

	@NotNull
	@Schema(description = "주간 미인증 알림 활성화 여부", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
	Boolean weeklyEnabled,

	@Schema(description = "팀원 인증/좋아요 소셜 알림 활성화 여부 (미입력 시 기존 값 유지)", example = "true", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
	Boolean socialEnabled,

	@NotNull
	@Schema(description = "마케팅 수신 동의 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
	Boolean marketingConsent
) {
}
