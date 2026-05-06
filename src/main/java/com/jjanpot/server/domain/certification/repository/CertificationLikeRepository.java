package com.jjanpot.server.domain.certification.repository;

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

	/** 특정 유저의 특정 인증 좋아요 조회 (토글용) */
	java.util.Optional<CertificationLike> findByCertificationAndUser(Certification certification, User user);

	/** 특정 유저가 특정 인증에 활성 좋아요를 눌렀는지 확인 */
	boolean existsByCertificationAndUserAndDeletedAtIsNull(Certification certification, User user);

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
