package com.jjanpot.server.domain.notification_template.dto;

import com.jjanpot.server.domain.notification_template.entity.NotificationTemplateType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "알림 템플릿 생성 요청")
public record CreateNotificationTemplateRequest(
	@NotNull
	@Schema(
		description = "알림 템플릿 타입 (CERTIFICATION_CREATED: 팀원 신규 인증, LIKE: 좋아요, ENCOURAGE: 인증 독려)",
		example = "CERTIFICATION_CREATED",
		requiredMode = Schema.RequiredMode.REQUIRED
	)
	NotificationTemplateType type,

	@NotBlank
	@Size(min = 2)
	@Schema(
		description = "푸시 알림 제목. {nickname} 입력 시 액션 수행자 닉네임으로 치환됩니다.",
		example = "{nickname}님이 인증을 올렸어요 ✅",
		requiredMode = Schema.RequiredMode.REQUIRED
	)
	String title,

	@NotBlank
	@Size(min = 2)
	@Schema(
		description = "푸시 알림 본문. {nickname} 입력 시 액션 수행자 닉네임으로 치환됩니다.",
		example = "함께라서 더 든든한 절약! 팀원의 새로운 절약 인증을 확인해보세요 👀",
		requiredMode = Schema.RequiredMode.REQUIRED
	)
	String body
) {
}
