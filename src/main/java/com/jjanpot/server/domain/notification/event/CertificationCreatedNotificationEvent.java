package com.jjanpot.server.domain.notification.event;

/**
 * 인증 게시물 생성이 정상 커밋된 뒤 팀원 신규 인증 푸시 알림을 발송하기 위한 이벤트입니다.
 *
 * @param certificationId 새로 생성된 인증 게시물 ID
 * @param challengeId 인증이 등록된 챌린지 ID
 * @param actorUserId 인증을 등록한 사용자 ID
 * @param actorNickname 푸시 문구에 노출할 인증 작성자 닉네임
 */
public record CertificationCreatedNotificationEvent(
	Long certificationId,
	Long challengeId,
	Long actorUserId,
	String actorNickname
) {
}
