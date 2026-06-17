package com.jjanpot.server.domain.notification.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jjanpot.server.domain.challenge.entity.ChallengeStatus;
import com.jjanpot.server.domain.notification.dto.UserChallengeReminderDto;
import com.jjanpot.server.domain.notification.dto.UserFcmDto;
import com.jjanpot.server.domain.notification.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long>, NotificationRepositoryCustom {

	// TODO 고도화 작업시 유저 설정에 알람 동의한 유저만 조회하게 WHERE 조건에 추가 필요.
	@Query("""
			SELECT
				new com.jjanpot.server.domain.notification.dto.UserFcmDto(
				ud.user.userId, ud.fcmToken, MAX(c.challengeId)
			)
			FROM UserDevice ud
			INNER JOIN User u
				ON ud.user = u
			INNER JOIN UserNotificationSetting uns
				ON uns.user = u AND uns.dailyEnabled = true
			INNER JOIN TeamMembers tm
				ON u = tm.user
			INNER JOIN Team t
				ON tm.team = t
			INNER JOIN Challenge c
				ON t = c.team
					AND c.status = :ongoing
			WHERE 1 = 1
				AND NOT EXISTS (
					SELECT 1
						FROM Certification cert
		   	          	WHERE 1 = 1
		     	        	AND cert.user = u
							AND cert.challenge = c
		   	            	AND cert.createdAt BETWEEN :startOfToday AND :endOfToday
					)
			GROUP BY u.userId, ud.fcmToken
		""")
	List<UserFcmDto> findDidNotCertifyUserByToday(
		@Param("startOfToday") LocalDateTime startOfToday,
		@Param("endOfToday") LocalDateTime endOfToday,
		@Param("ongoing") ChallengeStatus ongoing
	);

	/**
	 *
	 * @param startDateTime 기준 시간
	 * @param endDateTime 일차의 끝시간
	 * @param day 몇일차인지 값 전달용
	 * @return
	 */
	@Query("""
			SELECT new com.jjanpot.server.domain.notification.dto.UserChallengeReminderDto(
				u.userId,
				ud.fcmToken,
				MAX(c.challengeId),
				:day
			)
			FROM UserDevice ud
			JOIN ud.user u
			JOIN UserNotificationSetting uns
				ON uns.user = u AND uns.weeklyEnabled = true
			JOIN TeamMembers tm
				ON tm.user = u
			JOIN Challenge c
				ON c.team = tm.team
			WHERE c.status = :ongoing
				AND c.startDate BETWEEN :startDateTime AND :endDateTime
				AND NOT EXISTS (
					SELECT 1 FROM Certification cert
					WHERE cert.user = u AND cert.challenge = c
						AND cert.createdAt BETWEEN :startDateTime AND :now
				)
			GROUP BY u.userId, ud.fcmToken
		""")
	List<UserChallengeReminderDto> findDidNotCertifyUserWeekly(
		@Param("startDateTime") LocalDateTime startDateTime,
		@Param("endDateTime") LocalDateTime endDateTime,
		@Param("now") LocalDateTime now,
		@Param("day") Long day,
		@Param("ongoing") ChallengeStatus ongoing
	);

	@Query("""
			SELECT new com.jjanpot.server.domain.notification.dto.UserFcmDto(
				u.userId,
				ud.fcmToken,
				c.challengeId
			)
			FROM UserDevice ud
			JOIN ud.user u
			JOIN UserNotificationSetting uns
				ON uns.user = u AND uns.socialEnabled = true
			JOIN TeamMembers tm
				ON tm.user = u
			JOIN Challenge c
				ON c.team = tm.team
			WHERE c.challengeId = :challengeId
				AND u.userId <> :authorUserId
				AND ud.isActive = true
		""")
	List<UserFcmDto> findSocialTargetsByChallengeExceptAuthor(
		@Param("challengeId") Long challengeId,
		@Param("authorUserId") Long authorUserId
	);

	@Query("""
			SELECT new com.jjanpot.server.domain.notification.dto.UserFcmDto(
				u.userId,
				ud.fcmToken,
				:challengeId
			)
			FROM UserDevice ud
			JOIN ud.user u
			JOIN UserNotificationSetting uns
				ON uns.user = u AND uns.socialEnabled = true
			WHERE u.userId = :ownerUserId
				AND ud.isActive = true
		""")
	List<UserFcmDto> findSocialTargetsByUser(
		@Param("ownerUserId") Long ownerUserId,
		@Param("challengeId") Long challengeId
	);

	@Modifying
	@Query("DELETE FROM Notification n WHERE n.userId = :userId")
	void deleteAllByUserId(@Param("userId") Long userId);
}
