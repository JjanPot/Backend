package com.jjanpot.server.domain.certification.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jjanpot.server.domain.certification.entity.Certification;
import com.jjanpot.server.domain.certification.entity.CertificationLike;
import com.jjanpot.server.domain.user.entity.User;

public interface CertificationLikeRepository extends JpaRepository<CertificationLike, Long> {

	/** 인증별 활성 좋아요 수 조회 (피드 조회 시 활용) */
	int countByCertificationAndDeletedAtIsNull(Certification certification);

	/** 인증 ID 목록에 대한 활성 좋아요 수 일괄 조회 */
	@Query("SELECT cl.certification.certificationId, COUNT(cl) "
		+ "FROM CertificationLike cl "
		+ "WHERE cl.certification.certificationId IN :certificationIds "
		+ "AND cl.deletedAt IS NULL "
		+ "GROUP BY cl.certification.certificationId")
	List<Object[]> countActiveLikesByCertificationIds(@Param("certificationIds") List<Long> certificationIds);

	/** 특정 유저의 특정 인증 좋아요 조회 (토글용) */
	java.util.Optional<CertificationLike> findByCertificationAndUser(Certification certification, User user);

	/** 특정 유저가 특정 인증에 활성 좋아요를 눌렀는지 확인 */
	boolean existsByCertificationAndUserAndDeletedAtIsNull(Certification certification, User user);

	/** 특정 유저가 좋아요한 인증 ID 목록 일괄 조회 */
	@Query("SELECT cl.certification.certificationId "
		+ "FROM CertificationLike cl "
		+ "WHERE cl.certification.certificationId IN :certificationIds "
		+ "AND cl.user.userId = :userId "
		+ "AND cl.deletedAt IS NULL")
	List<Long> findLikedCertificationIdsByUserId(
		@Param("certificationIds") List<Long> certificationIds,
		@Param("userId") Long userId
	);

	/** 인증 삭제 시 연관 좋아요 일괄 삭제 */
	void deleteByCertification(Certification certification);

	@Modifying
	@Query("DELETE FROM CertificationLike cl WHERE cl.certification.user = :user")
	void deleteByCertificationUser(@Param("user") User user);

	/** 유저의 좋아요 일괄 삭제 (회원 탈퇴용) */
	@Modifying
	@Query("DELETE FROM CertificationLike cl WHERE cl.user = :user")
	void deleteAllByUser(@Param("user") User user);
}
