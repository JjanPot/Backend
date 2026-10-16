package com.jjanpot.server.domain.certification.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jjanpot.server.domain.certification.entity.Certification;
import com.jjanpot.server.domain.challenge.entity.Challenge;
import com.jjanpot.server.domain.user.entity.User;

public interface CertificationRepository extends JpaRepository<Certification, Long> {

	/** 특정 챌린지에서 특정 유저의 인증 목록 조회 */
	// ChallengeScheduler에서 챌린지 종료 시 개인 결과 산출에 활용
	List<Certification> findAllByChallengeAndUser(Challenge challenge, User user);

	/** 하루 인증 횟수 조회 (일일 최대 3회 제한 체크용, 배타적 상한) */
	@Query("SELECT COUNT(c) FROM Certification c "
		+ "WHERE c.user = :user AND c.challenge = :challenge "
		+ "AND c.spentAt >= :start AND c.spentAt < :end")
	int countByUserAndChallengeAndSpentAtRange(
		@Param("user") User user,
		@Param("challenge") Challenge challenge,
		@Param("start") LocalDateTime start,
		@Param("end") LocalDateTime end
	);

	/** 챌린지 전체 인증 피드 조회 (최신순, 차단 사용자 제외, 신고 비노출 제외) */
	@Query("SELECT c FROM Certification c "
		+ "JOIN FETCH c.user "
		+ "JOIN FETCH c.category "
		+ "WHERE c.challenge = :challenge "
		+ "AND c.isHidden = false "
		+ "AND NOT EXISTS ("
		+ "  SELECT b FROM com.jjanpot.server.domain.block.entity.Block b "
		+ "  WHERE b.blocker.userId = :viewerId AND b.blocked = c.user AND b.challenge = :challenge"
		+ ") "
		+ "ORDER BY c.createdAt DESC")
	List<Certification> findFeedExcludingBlocked(
		@Param("challenge") Challenge challenge,
		@Param("viewerId") Long viewerId
	);

	/** 챌린지 내 팀원별 절약 금액 합산 조회 */
	@Query("SELECT c.user.userId, COALESCE(SUM(c.savedAmount), 0) "
		+ "FROM Certification c "
		+ "WHERE c.challenge = :challenge "
		+ "GROUP BY c.user.userId")
	List<Object[]> sumSavedAmountPerUserByChallenge(@Param("challenge") Challenge challenge);

	/** 챌린지 내 팀원별 인증 횟수 조회 */
	@Query("SELECT c.user.userId, COUNT(c) "
		+ "FROM Certification c "
		+ "WHERE c.challenge = :challenge "
		+ "GROUP BY c.user.userId")
	List<Object[]> countCertPerUserByChallenge(@Param("challenge") Challenge challenge);

	/** 챌린지 내 특정 유저의 인증 날짜 목록 조회 (중복 제거, 정렬) */
	@Query("SELECT DISTINCT CAST(c.spentAt AS date) "
		+ "FROM Certification c "
		+ "WHERE c.challenge = :challenge AND c.user = :user "
		+ "ORDER BY CAST(c.spentAt AS date)")
	List<Object> findDistinctCertDatesByUser(
		@Param("challenge") Challenge challenge,
		@Param("user") User user
	);

	/** 챌린지 내 전체 인증 날짜별 유저 수 조회 (팀 연속활동 계산용) */
	@Query("SELECT CAST(c.spentAt AS date), COUNT(DISTINCT c.user.userId) "
		+ "FROM Certification c "
		+ "WHERE c.challenge = :challenge "
		+ "GROUP BY CAST(c.spentAt AS date) "
		+ "ORDER BY CAST(c.spentAt AS date)")
	List<Object[]> countDistinctUsersByDateForChallenge(@Param("challenge") Challenge challenge);

	/** 유저의 인증 일괄 삭제 (회원 탈퇴용) */
	@Modifying
	@Query("DELETE FROM Certification c WHERE c.user = :user")
	void deleteAllByUser(@Param("user") User user);
}
