package com.jjanpot.server.domain.notification.event;

/**
 * 인증 게시물에 최초 좋아요가 생성된 뒤 게시물 작성자에게 푸시 알림을 발송하기 위한 이벤트입니다.
 *
 * @param certificationId 좋아요가 눌린 인증 게시물 ID
 * @param challengeId 인증 게시물이 속한 챌린지 ID
 * @param actorUserId 좋아요를 누른 사용자 ID
 * @param ownerUserId 좋아요를 받은 인증 게시물 작성자 ID
 * @param actorNickname 푸시 문구에 노출할 좋아요 누른 사용자 닉네임
 */
public record CertificationLikedNotificationEvent(
	Long certificationId,
	Long challengeId,
	Long actorUserId,
	Long ownerUserId,
	String actorNickname
) {
}
