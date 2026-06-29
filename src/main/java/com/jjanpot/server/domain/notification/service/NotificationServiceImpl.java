package com.jjanpot.server.domain.notification.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.google.common.collect.Lists;
import com.jjanpot.server.domain.challenge.entity.ChallengeStatus;
import com.jjanpot.server.domain.notification.dto.FcmSendCommand;
import com.jjanpot.server.domain.notification.dto.UserChallengeReminderDto;
import com.jjanpot.server.domain.notification.dto.UserFcmDto;
import com.jjanpot.server.domain.notification.dto.response.NotificationResponse;
import com.jjanpot.server.domain.notification.entity.Notification;
import com.jjanpot.server.domain.notification.repository.NotificationRepository;
import com.jjanpot.server.domain.notification_template.entity.NotificationSubTemplateType;
import com.jjanpot.server.domain.notification_template.entity.NotificationTemplate;
import com.jjanpot.server.domain.notification_template.entity.NotificationTemplateType;
import com.jjanpot.server.domain.notification_template.repository.NotificationTemplateRepository;
import com.jjanpot.server.domain.user.repository.UserRepository;
import com.jjanpot.server.global.exception.BusinessException;
import com.jjanpot.server.global.exception.ErrorCode;
import com.jjanpot.server.global.service.push.PushSendService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {
	private final PushSendService pushSendService;
	private final NotificationTemplateRepository notificationTemplateRepository;
	private final NotificationRepository notificationRepository;
	private final NotificationManager notificationManager;
	private final UserRepository userRepository;

	@Value("${custom.fcm.partition-size:500}")
	private int partitionSize;

	@Override
	@Transactional(readOnly = true)
	public List<NotificationResponse> getNotifications(Long userId) {
		if (!userRepository.existsById(userId)) {
			throw new BusinessException(ErrorCode.USER_NOT_FOUND);
		}

		return notificationRepository.findInboxByUserId(userId).stream()
			.map(NotificationResponse::from)
			.toList();
	}

	@Override
	public void sendDailyReminder() {
		log.info("Daily Reminder Process Started");
		LocalDateTime start = LocalDate.now().atStartOfDay();
		LocalDateTime end = start.plusDays(1).minusNanos(1);

		List<UserFcmDto> didNotCertifyUserByPeriod = notificationRepository.findDidNotCertifyUserByToday(start, end,
			ChallengeStatus.ONGOING);

		NotificationSubTemplateType daily = NotificationSubTemplateType.DAILY;
		List<NotificationTemplate> templateList =
			notificationTemplateRepository.findTemplateByType(
				NotificationTemplateType.from(daily.getGroupCode()),
				daily
			);

		processReminder(didNotCertifyUserByPeriod, templateList);

		log.info("Daily Reminder Process Ended");
	}

	@Override
	public void sendWeeklyReminder() {
		log.info("Weekly Reminder Process Started");
		LocalDate today = LocalDate.now();

		Map<Integer, NotificationSubTemplateType> dayMap = Map.of(
			3, NotificationSubTemplateType.START_OF_WEEK,
			5, NotificationSubTemplateType.MIDDLE_OF_WEEK,
			7, NotificationSubTemplateType.END_OF_WEEK
		);

		for (var entry : dayMap.entrySet()) {
			Integer day = entry.getKey();

			// 오늘 - (N-1)일
			LocalDateTime challengeStartDate = today.minusDays(day - 1).atStartOfDay();

			// 인증 체크 종료 시간 (그날의 23:59:59)
			LocalDateTime endDateTime = challengeStartDate.with(LocalTime.MAX).withNano(0);
			LocalDateTime nowTime = LocalDateTime.now();

			List<UserChallengeReminderDto> targets =
				notificationRepository.findDidNotCertifyUserWeekly(challengeStartDate, endDateTime, nowTime, day.longValue(),
					ChallengeStatus.ONGOING);

			if (CollectionUtils.isEmpty(targets)) {
				continue;
			}

			List<UserFcmDto> targetUserFcmDtoList = targets.stream()
				.map(reminderDto ->
					new UserFcmDto(reminderDto.userId(), reminderDto.fcmToken(), reminderDto.challengeId()))
				.toList();

			List<NotificationTemplate> templates = notificationTemplateRepository
				.findTemplateByType(NotificationTemplateType.ENCOURAGE, entry.getValue());

			processReminder(targetUserFcmDtoList, templates);
		}

		log.info("Weekly Reminder Process Ended");
	}

	private void processReminder(List<UserFcmDto> targetUserFcmDtoList, List<NotificationTemplate> templateList) {
		if (CollectionUtils.isEmpty(targetUserFcmDtoList)) {
			return;
		}

		if (CollectionUtils.isEmpty(templateList)) {
			throw new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND);
		}

		List<Notification> allNotifications = initNotifications(targetUserFcmDtoList, templateList);

		sendInBatches(allNotifications);
	}

	private void sendInBatches(List<Notification> notifications) {
		log.info("sendInBatches Started");
		Lists.partition(notifications, partitionSize).forEach(partition -> {
			List<FcmSendCommand> commands = partition.stream()
				.map(FcmSendCommand::from)
				.toList();

			pushSendService.sendMessage(commands)
				.thenAccept(results -> {
					notificationManager.updateResults(partition, results);
				})
				.exceptionally(ex -> {
					log.error("FCM 배치 발송 중 오류 발생: {}", ex.getMessage());
					notificationManager.markAsFailed(partition, ex.getMessage());
					return null;
				});
		});
		log.info("sendInBatches Ended");
	}

	@Override
	@Transactional
	public void markAsRead(Long userId, Long notificationId) {
		userRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

		Notification notification = notificationRepository.findById(notificationId)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));

		// 본인의 알림인지 권한 확인
		if (!notification.getUserId().equals(userId)) {
			throw new BusinessException(ErrorCode.NOTIFICATION_UNAUTHORIZED);
		}

		notification.markAsRead();

		notificationRepository.save(notification);
	}

	/**
	 * Notification 초기화
	 * @param targets 알림 발송 유저 fcm 정보
	 * @param templates 알림 템플릿
	 * @return 초기화된 알림 발송내역
	 */
	private List<Notification> initNotifications(List<UserFcmDto> targets, List<NotificationTemplate> templates) {
		Random random = ThreadLocalRandom.current();

		List<Notification> list = targets.stream()
			.map(target -> {
				if (!StringUtils.hasText(target.fcmToken())) {
					return null;
				}
				NotificationTemplate selectedTemplate = templates.get(random.nextInt(templates.size()));

				// PENDING 상태 생성
				return Notification.create(target.userId(), target.fcmToken(), selectedTemplate, target.challengeId());
			})
			.filter(Objects::nonNull)
			.toList();

		return notificationManager.saveNotifications(list);
	}
}
