package com.jjanpot.server.domain.notification.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

import com.jjanpot.server.domain.notification.dto.FcmSendCommand;
import com.jjanpot.server.domain.notification.dto.UserFcmDto;
import com.jjanpot.server.domain.notification.entity.Notification;
import com.jjanpot.server.domain.notification.event.CertificationCreatedNotificationEvent;
import com.jjanpot.server.domain.notification.event.CertificationLikedNotificationEvent;
import com.jjanpot.server.domain.notification.repository.NotificationRepository;
import com.jjanpot.server.domain.notification_template.entity.NotificationTemplate;
import com.jjanpot.server.domain.notification_template.entity.NotificationTemplateType;
import com.jjanpot.server.domain.notification_template.repository.NotificationTemplateRepository;
import com.jjanpot.server.global.service.push.PushSendService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// - 인증 생성 이벤트 수신
// - 좋아요 이벤트 수신
// - 대상 유저 조회
// - 템플릿에 닉네임 매핑
// - Notification 저장
// - FCM 발송

@Slf4j
@Service
@RequiredArgsConstructor
public class SocialNotificationService {

	private static final String UTM_SOURCE = "push_notification";
	private static final String UTM_CAMPAIGN = "social_interaction";

	private final PushSendService pushSendService;
	private final NotificationRepository notificationRepository;
	private final NotificationTemplateRepository notificationTemplateRepository;
	private final NotificationManager notificationManager;

	/**
	 * 인증 생성 트랜잭션이 커밋된 뒤 팀원 신규 인증 알림을 발송
	 * 작성자는 수신 대상에서 제외하고, socialEnabled=true이며 활성 FCM 토큰이 있는 팀원만 조회
	 */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleCertificationCreated(CertificationCreatedNotificationEvent event) {
		try {
			List<UserFcmDto> targets = notificationRepository.findSocialTargetsByChallengeExceptAuthor(
				event.challengeId(),
				event.actorUserId()
			);

			sendSocialNotification(
				targets,
				NotificationTemplateType.CERTIFICATION_CREATED,
				event.certificationId(),
				event.challengeId(),
				event.actorNickname()
			);
		} catch (Exception ex) {
			log.error("[팀원 신규 인증 알림] 발송 처리 실패 certificationId={}, challengeId={}",
				event.certificationId(), event.challengeId(), ex);
		}
	}

	/**
	 * 최초 좋아요 생성 트랜잭션이 커밋된 뒤 인증 게시물 작성자에게 좋아요 알림을 발송
	 * 좋아요 취소 후 재클릭 중복 방지는 CertificationLikeService에서 최초 생성 시에만 이벤트를 발행하는 방식으로 처리
	 */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleCertificationLiked(CertificationLikedNotificationEvent event) {
		if (event.actorUserId().equals(event.ownerUserId())) {
			return;
		}

		try {
			List<UserFcmDto> targets = notificationRepository.findSocialTargetsByUser(
				event.ownerUserId(),
				event.challengeId()
			);

			sendSocialNotification(
				targets,
				NotificationTemplateType.LIKE,
				event.certificationId(),
				event.challengeId(),
				event.actorNickname()
			);
		} catch (Exception ex) {
			log.error("[내 피드 좋아요 알림] 발송 처리 실패 certificationId={}, ownerUserId={}",
				event.certificationId(), event.ownerUserId(), ex);
		}
	}

	/**
	 * 소셜 알림 공통 발송 흐름
	 * 템플릿의 {nickname} 변수를 실제 액션 수행자 닉네임으로 치환하고,
	 * 알림 이력을 저장한 뒤 FCM data payload와 함께 푸시를 전송
	 */
	private void sendSocialNotification(
		List<UserFcmDto> targets,
		NotificationTemplateType type,
		Long certificationId,
		Long challengeId,
		String actorNickname
	) {
		if (targets == null || targets.isEmpty()) {
			return;
		}

		NotificationTemplate template = notificationTemplateRepository.findByType(type)
			.stream()
			.findFirst()
			.orElse(null);
		if (template == null) {
			log.warn("[소셜 알림] 템플릿 없음 type={}", type);
			return;
		}

		String title = render(template.getTitle(), actorNickname);
		String body = render(template.getBody(), actorNickname);
		Map<String, String> data = createSocialData(certificationId, challengeId);

		List<Notification> notifications = targets.stream()
			.filter(target -> StringUtils.hasText(target.fcmToken()))
			.map(target -> Notification.create(
				target.userId(),
				target.fcmToken(),
				template,
				certificationId,
				title,
				body
			))
			.toList();

		if (notifications.isEmpty()) {
			return;
		}

		List<Notification> savedNotifications = notificationManager.saveNotifications(notifications);
		List<FcmSendCommand> commands = savedNotifications.stream()
			.map(notification -> new FcmSendCommand(
				notification.getTargetToken(),
				notification.getTitle(),
				notification.getBody(),
				type,
				notification.getRelatedId(),
				data
			))
			.toList();

		pushSendService.sendMessage(commands)
			.thenAccept(results -> notificationManager.updateResults(savedNotifications, results))
			.exceptionally(ex -> {
				log.error("[소셜 알림] FCM 발송 실패 type={}, certificationId={}", type, certificationId, ex);
				notificationManager.markAsFailed(savedNotifications, ex.getMessage());
				return null;
			});
	}

	/**
	 * 앱 라우팅과 푸시 오픈율 추적에 필요한 FCM data payload를 생성
	 */
	private Map<String, String> createSocialData(Long certificationId, Long challengeId) {
		return Map.of(
			"challengeId", String.valueOf(challengeId),
			"certificationId", String.valueOf(certificationId),
			"deepLink", createDeepLink(certificationId),
			"utm_source", UTM_SOURCE,
			"utm_campaign", UTM_CAMPAIGN
		);
	}

	/**
	 * 소셜 알림 클릭 시 인증 상세 화면으로 이동하기 위한 딥링크
	 */
	private String createDeepLink(Long certificationId) {
		return "jjanpot://certifications/" + certificationId
			+ "?utm_source=" + UTM_SOURCE
			+ "&utm_campaign=" + UTM_CAMPAIGN;
	}

	/**
	 * 알림 템플릿에 포함된 닉네임 플레이스홀더를 실제 닉네임으로 치환
	 */
	private String render(String template, String actorNickname) {
		return template.replace("{nickname}", actorNickname);
	}
}
