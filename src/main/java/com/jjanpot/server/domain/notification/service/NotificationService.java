package com.jjanpot.server.domain.notification.service;

import java.util.List;

import com.jjanpot.server.domain.notification.dto.response.NotificationResponse;

public interface NotificationService {
	List<NotificationResponse> getNotifications(Long userId);

	void sendDailyReminder();

	void sendWeeklyReminder();

	void markAsRead(Long userId, Long notificationId);
}
