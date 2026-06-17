package com.jjanpot.server.domain.notification.dto;

import java.util.Map;

import com.jjanpot.server.domain.notification.entity.Notification;
import com.jjanpot.server.domain.notification_template.entity.NotificationTemplateType;

/**
 * FCM 전송 계층에 전달하는 푸시 발송 명령 객체입니다.
 *
 * 기존 리마인더 알림은 type/relatedId만 사용하고, 소셜 알림은 data에
 * challengeId, certificationId, deepLink, utm 파라미터를 추가로 담아 앱 화면 이동과 오픈율 추적에 사용합니다.
 *
 * @param targetToken FCM 대상 토큰
 * @param title 푸시 알림 제목
 * @param body 푸시 알림 본문
 * @param type 알림 타입
 * @param relatedId 기존 호환용 연관 리소스 ID
 * @param data 알림 타입별 추가 FCM data payload
 */
public record FcmSendCommand (
	String targetToken,
	String title,
	String body,
	NotificationTemplateType type,
	Long relatedId,
	Map<String, String> data
) {
	/**
	 * 기존 리마인더 알림처럼 추가 data payload가 필요 없는 경우 사용하는 생성자입니다.
	 */
	public FcmSendCommand(
		String targetToken,
		String title,
		String body,
		NotificationTemplateType type,
		Long relatedId
	) {
		this(targetToken, title, body, type, relatedId, Map.of());
	}

	/**
	 * DB에 저장된 알림 내역을 FCM 전송 명령으로 변환합니다.
	 * 저장된 Notification에는 소셜 알림의 deepLink/utm 정보가 없기 때문에 추가 data는 빈 값으로 둡니다.
	 */
	public static FcmSendCommand from(Notification notification) {
		return new FcmSendCommand(
			notification.getTargetToken(),
			notification.getTitle(),
			notification.getBody(),
			notification.getNotificationTemplate().getType(),
			notification.getRelatedId(),
			Map.of()
		);
	}
}
