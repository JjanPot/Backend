package com.jjanpot.server.domain.certification.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jjanpot.server.domain.certification.dto.response.ToggleLikeResponse;
import com.jjanpot.server.domain.certification.entity.Certification;
import com.jjanpot.server.domain.certification.entity.CertificationLike;
import com.jjanpot.server.domain.certification.repository.CertificationLikeRepository;
import com.jjanpot.server.domain.certification.repository.CertificationRepository;
import com.jjanpot.server.domain.user.entity.User;
import com.jjanpot.server.domain.user.repository.UserRepository;
import com.jjanpot.server.global.exception.BusinessException;
import com.jjanpot.server.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CertificationLikeService {

    private final CertificationLikeRepository certificationLikeRepository;
    private final CertificationRepository certificationRepository;
    private final UserRepository userRepository;

    /**
     * 좋아요 토글
     * - 좋아요 이력 없음 → 새로 생성
     * - 좋아요 취소 상태 → 재활성화
     * - 좋아요 활성 상태 → 취소 (soft delete)
     *
     * @return true: 좋아요 활성, false: 좋아요 취소
     */
    @Transactional
    public ToggleLikeResponse toggleLike(Long userId, Long certificationId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Certification certification = certificationRepository.findById(certificationId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CERTIFICATION_NOT_FOUND));

        boolean isLiked = certificationLikeRepository.findByCertificationAndUser(certification, user)
            .map(like -> {
                if (like.isDeleted()) {
                    like.restore();
                    return true;
                } else {
                    like.delete();
                    return false;
                }
            })
            .orElseGet(() -> {
                certificationLikeRepository.save(
                    CertificationLike.builder()
                        .certification(certification)
                        .user(user)
                        .build()
                );
                return true;
            });

        int likeCount = certificationLikeRepository.countByCertificationAndDeletedAtIsNull(certification);
        return new ToggleLikeResponse(isLiked, likeCount);
    }
}
