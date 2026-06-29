package com.jjanpot.server.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.jjanpot.server.domain.notification.dto.response.NotificationResponse;
import com.jjanpot.server.domain.notification.entity.Notification;
import com.jjanpot.server.domain.notification.repository.NotificationRepository;
import com.jjanpot.server.domain.notification_template.entity.NotificationTemplate;
import com.jjanpot.server.domain.notification_template.entity.NotificationTemplateType;
import com.jjanpot.server.domain.notification_template.repository.NotificationTemplateRepository;
import com.jjanpot.server.domain.user.repository.UserRepository;
import com.jjanpot.server.global.exception.BusinessException;
import com.jjanpot.server.global.exception.ErrorCode;
import com.jjanpot.server.global.service.push.PushSendService;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService 단위 테스트")
class NotificationServiceImplTest {

	@Mock
	private PushSendService pushSendService;

	@Mock
	private NotificationTemplateRepository notificationTemplateRepository;

	@Mock
	private NotificationRepository notificationRepository;

	@Mock
	private NotificationManager notificationManager;

	@Mock
	private UserRepository userRepository;

	@InjectMocks
	private NotificationServiceImpl notificationService;

	@Test
	@DisplayName("알림함을 조회하면 알림 응답 목록을 반환한다")
	void getNotifications() {
		Long userId = 1L;
		LocalDateTime receivedAt = LocalDateTime.of(2026, 6, 29, 18, 0);
		NotificationTemplate template = NotificationTemplate.builder()
			.templateId(10L)
			.type(NotificationTemplateType.LIKE)
			.title("좋아요 알림")
			.body("좋아요가 달렸어요.")
			.build();
		Notification notification = Notification.builder()
			.notificationId(101L)
			.userId(userId)
			.notificationTemplate(template)
			.targetToken("fcm-token")
			.relatedId(282L)
			.title("내 인증에 좋아요가 달렸어요")
			.body("짠돌이님이 회원님의 인증을 좋아합니다.")
			.isRead(false)
			.status(Notification.NotificationStatus.SENT)
			.messageId("message-id")
			.build();
		ReflectionTestUtils.setField(notification, "createdAt", receivedAt);

		when(userRepository.existsById(userId)).thenReturn(true);
		when(notificationRepository.findInboxByUserId(userId)).thenReturn(List.of(notification));

		List<NotificationResponse> responses = notificationService.getNotifications(userId);

		assertThat(responses).containsExactly(new NotificationResponse(
			101L,
			NotificationTemplateType.LIKE,
			"내 인증에 좋아요가 달렸어요",
			"짠돌이님이 회원님의 인증을 좋아합니다.",
			282L,
			false,
			receivedAt
		));
	}

	@Test
	@DisplayName("존재하지 않는 사용자가 알림함을 조회하면 예외가 발생한다")
	void getNotificationsWithMissingUser() {
		Long userId = 999L;
		when(userRepository.existsById(userId)).thenReturn(false);

		assertThatThrownBy(() -> notificationService.getNotifications(userId))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.USER_NOT_FOUND);

		verify(notificationRepository, never()).findInboxByUserId(userId);
	}
}
