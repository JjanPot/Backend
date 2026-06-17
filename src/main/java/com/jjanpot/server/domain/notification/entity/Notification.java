package com.jjanpot.server.domain.notification.entity;

import com.jjanpot.server.domain.notification_template.entity.NotificationTemplate;
import com.jjanpot.server.global.entity.BaseEntity;
import com.jjanpot.server.global.util.CodeEnum;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Notification extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "notification_id")
	private Long notificationId;

	@Column(name = "user_id")
	private Long userId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "template_id", nullable = false)
	private NotificationTemplate notificationTemplate;

	@Column(name = "target_token", nullable = false, length = 3000)
	private String targetToken;

	@Column(name = "related_id")
	private Long relatedId;

	@Enumerated(EnumType.STRING)
	private NotificationStatus status;

	@Column(name = "title", nullable = false, length = 3000)
	private String title;

	@Column(name = "body", nullable = false, length = 3000)
	private String body;

	@Column(name = "is_read")
	@Builder.Default
	private Boolean isRead = false;

	@Column(name = "message_id", nullable = false, length = 200)
	private String messageId;

	@Column(name = "fail_code")
	private String failCode;

	@Column(name = "fail_reason")
	private String failReason;

	public static Notification create(Long userId, String fcmToken, NotificationTemplate template, Long relatedId) {
		return create(userId, fcmToken, template, relatedId, template.getTitle(), template.getBody());
	}

	public static Notification create(
		Long userId,
		String fcmToken,
		NotificationTemplate template,
		Long relatedId,
		String title,
		String body
	) {
		return Notification.builder()
			.userId(userId)
			.targetToken(fcmToken)
			.notificationTemplate(template)
			.title(title)
			.body(body)
			.status(NotificationStatus.PENDING)
			.relatedId(relatedId)
			.isRead(false)
			.build();
	} // 템플릿은 유지하되, 발송 시점에 가공한 title/body도 저장 가능

	public void markAsRead() {
		this.isRead = true;
	}

	public void success(String messageId) {
		this.messageId = messageId;
		this.status = NotificationStatus.SENT;
	}

	public void fail(String failCode, String reason) {
		this.failCode = failCode;
		this.failReason = reason;
		this.status = NotificationStatus.FAILED;
	}

	@Getter
	@RequiredArgsConstructor
	public enum NotificationStatus implements CodeEnum {
		PENDING("PENDING", "대기중"),
		SENT("SENT", "발송 완료"),
		FAILED("FAILED", "실패"),
		;

		private final String code;
		private final String description;
	}
}
