package com.jjanpot.server.domain.notification.dto.response;

import java.time.LocalDateTime;

import com.jjanpot.server.domain.notification.entity.Notification;
import com.jjanpot.server.domain.notification_template.entity.NotificationTemplateType;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "알림함 알림")
public record NotificationResponse(
	@Schema(description = "알림 ID", example = "101")
	Long notificationId,

	@Schema(description = "알림 타입", example = "LIKE")
	NotificationTemplateType type,

	@Schema(description = "알림 제목", example = "내 인증에 좋아요가 달렸어요")
	String title,

	@Schema(description = "알림 내용", example = "짠돌이님이 회원님의 인증을 좋아합니다.")
	String body,

	@Schema(description = "연관 리소스 ID", example = "282", nullable = true)
	Long relatedId,

	@Schema(description = "읽음 여부", example = "false")
	boolean isRead,

	@Schema(description = "알림 수신 시각", example = "2026-06-29T18:00:00")
	LocalDateTime receivedAt
) {
	public static NotificationResponse from(Notification notification) {
		return new NotificationResponse(
			notification.getNotificationId(),
			notification.getNotificationTemplate().getType(),
			notification.getTitle(),
			notification.getBody(),
			notification.getRelatedId(),
			Boolean.TRUE.equals(notification.getIsRead()),
			notification.getCreatedAt()
		);
	}
}
