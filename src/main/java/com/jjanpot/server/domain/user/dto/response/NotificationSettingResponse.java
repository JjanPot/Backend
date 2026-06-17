package com.jjanpot.server.domain.user.dto.response;

import com.jjanpot.server.domain.user.entity.UserNotificationSetting;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "알림 설정 조회 응답")
public record NotificationSettingResponse(

	@Schema(description = "1일 1회 미인증 알림 활성화 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
	boolean dailyEnabled,

	@Schema(description = "주간 미인증 알림 활성화 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
	boolean weeklyEnabled,

	@Schema(description = "팀원 인증/좋아요 소셜 알림 활성화 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
	boolean socialEnabled,

	@Schema(description = "마케팅 수신 동의 여부", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
	boolean marketingConsent
) {
	public static NotificationSettingResponse of(UserNotificationSetting setting) {
		return new NotificationSettingResponse(
			setting.isDailyEnabled(),
			setting.isWeeklyEnabled(),
			setting.isSocialEnabled(),
			setting.isMarketingConsentEnabled()
		);
	}
}
