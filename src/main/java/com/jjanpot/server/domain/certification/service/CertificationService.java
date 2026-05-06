package com.jjanpot.server.domain.certification.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.jjanpot.server.domain.category.entity.Category;
import com.jjanpot.server.domain.certification.dto.request.CreateCertificationRequest;
import com.jjanpot.server.domain.certification.dto.response.CertificationFeedResponse;
import com.jjanpot.server.domain.certification.dto.response.CreateCertificationResponse;
import com.jjanpot.server.domain.certification.entity.Certification;
import com.jjanpot.server.domain.certification.entity.SpendType;
import com.jjanpot.server.domain.certification.repository.CertificationLikeRepository;
import com.jjanpot.server.domain.certification.repository.CertificationRepository;
import com.jjanpot.server.domain.challenge.entity.Challenge;
import com.jjanpot.server.domain.challenge.entity.ChallengeCategory;
import com.jjanpot.server.domain.challenge.entity.ChallengeStatus;
import com.jjanpot.server.domain.challenge.entity.ChallengeWeek;
import com.jjanpot.server.domain.challenge.repository.ChallengeCategoryRepository;
import com.jjanpot.server.domain.challenge.repository.ChallengeRepository;
import com.jjanpot.server.domain.challenge.repository.ChallengeWeekRepository;
import com.jjanpot.server.domain.team.repository.TeamMembersRepository;
import com.jjanpot.server.domain.user.entity.User;
import com.jjanpot.server.domain.user.repository.UserRepository;
import com.jjanpot.server.global.common.service.ImageUploadService;
import com.jjanpot.server.global.exception.BusinessException;
import com.jjanpot.server.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CertificationService {

	private static final int DAILY_CERT_LIMIT = 3;
	private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
	private static final String CERTIFICATION_FILE_NAME = "certification/";

	private final UserRepository userRepository;
	private final ChallengeRepository challengeRepository;
	private final ChallengeCategoryRepository challengeCategoryRepository;
	private final ChallengeWeekRepository challengeWeekRepository;
	private final TeamMembersRepository teamMembersRepository;
	private final CertificationRepository certificationRepository;
	private final CertificationLikeRepository certificationLikeRepository;
	private final ImageUploadService imageUploadService;

	/** 인증 생성 **/
	@Transactional
	public CreateCertificationResponse createCertification(Long userId, CreateCertificationRequest request,
		MultipartFile image) {
		User user = findUser(userId);
		Challenge challenge = findChallenge(request.challengeId());

		// 팀원 검증
		teamMembersRepository.findByTeamAndUser(challenge.getTeam(), user)
			.orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));

		// 챌린지 진행 중 여부 검증
		if (challenge.getStatus() != ChallengeStatus.ONGOING) {
			throw new BusinessException(ErrorCode.CHALLENGE_NOT_ONGOING);
		}

		// 지출 일시 검증: 미래 일시 불가
		LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
		if (request.spentAt().isAfter(now)) {
			throw new BusinessException(ErrorCode.CERTIFICATION_SPENT_AT_FUTURE);
		}

		// 지출 일시 검증: 챌린지 기간 범위 확인
		if (request.spentAt().isBefore(challenge.getStartDate())
			|| request.spentAt().isAfter(challenge.getEndDate())) {
			throw new BusinessException(ErrorCode.CERTIFICATION_SPENT_AT_OUT_OF_RANGE);
		}

		// 일일 인증 횟수 제한 검증 (spentAt 날짜 기준, 하루 최대 3회)
		LocalDate spentDate = request.spentAt().toLocalDate();
		LocalDateTime startOfDay = spentDate.atStartOfDay();
		LocalDateTime startOfNextDay = spentDate.plusDays(1).atStartOfDay();
		int todayCount = certificationRepository.countByUserAndChallengeAndSpentAtRange(
			user, challenge, startOfDay, startOfNextDay);
		if (todayCount >= DAILY_CERT_LIMIT) {
			throw new BusinessException(ErrorCode.CERTIFICATION_DAILY_LIMIT_EXCEEDED);
		}

		// 챌린지에 설정된 카테고리 검증
		ChallengeCategory challengeCategory = challengeCategoryRepository
			.findByChallengeAndCategory_CategoryId(challenge, request.categoryId())
			.orElseThrow(() -> new BusinessException(ErrorCode.CERTIFICATION_CATEGORY_NOT_IN_CHALLENGE));

		// 지출 금액 및 절약 금액 계산
		int spendAmount = resolveSpendAmount(request);
		int savedAmount = challengeCategory.getAmount() - spendAmount;

		// 현재 주차 조회 (MVP: 1주 고정, 비관적 잠금)
		ChallengeWeek currentWeek = challengeWeekRepository
			.findByChallengeAndWeekNumberForUpdate(challenge, 1)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

		// 이미지 업로드 (트랜잭션 롤백 시 업로드된 이미지 정리)
		String imageUrl = imageUploadService.upload(image, CERTIFICATION_FILE_NAME);

		// 인증 저장
		Category category = challengeCategory.getCategory();
		Certification certification = Certification.create(
			challenge, user, category, currentWeek,
			request.spendType(),
			spendAmount,
			savedAmount,
			request.memo() != null ? request.memo() : "",
			imageUrl,
			request.spentAt()
		);
		certificationRepository.save(certification);

		// 주차 절약 금액 누적
		currentWeek.addSavedAmount(savedAmount);

		log.info("[인증 생성] userId={}, challengeId={}, spendType={}, savedAmount={}",
			userId, request.challengeId(), request.spendType(), savedAmount);

		return CreateCertificationResponse.from(certification);
	}

	/** 챌린지 인증 피드 조회 (최신순) **/
	public List<CertificationFeedResponse> getCertificationFeed(Long userId, Long challengeId) {
		User user = findUser(userId);
		Challenge challenge = findChallenge(challengeId);

		// 팀원 검증
		teamMembersRepository.findByTeamAndUser(challenge.getTeam(), user)
			.orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));

		return certificationRepository.findFeedExcludingBlocked(challenge, userId)
			.stream()
			.map(cert -> CertificationFeedResponse.from(
				cert,
				certificationLikeRepository.countByCertificationAndDeletedAtIsNull(cert),
				userId,
				certificationLikeRepository.existsByCertificationAndUserAndDeletedAtIsNull(cert,
					userRepository.getReferenceById(userId))
			))
			.toList();
	}

	/** 인증 수정 **/
	@Transactional
	public CreateCertificationResponse updateCertification(Long userId, Long certificationId,
		CreateCertificationRequest request, MultipartFile image) {
		return updateCertification(userId, certificationId, request, image, false);
	}

	/** 인증 수정 **/
	@Transactional
	public CreateCertificationResponse updateCertification(Long userId, Long certificationId,
		CreateCertificationRequest request, MultipartFile image, boolean deleteImage) {
		User user = findUser(userId);
		Certification certification = findCertification(certificationId);

		// 본인 인증 검증
		validateOwner(certification, user);

		Challenge challenge = certification.getChallenge();

		// 챌린지 진행 중 여부 검증
		if (challenge.getStatus() != ChallengeStatus.ONGOING) {
			throw new BusinessException(ErrorCode.CHALLENGE_NOT_ONGOING);
		}

		// 지출 일시 검증: 미래 일시 불가
		LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
		if (request.spentAt().isAfter(now)) {
			throw new BusinessException(ErrorCode.CERTIFICATION_SPENT_AT_FUTURE);
		}

		// 지출 일시 검증: 챌린지 기간 범위 확인
		if (request.spentAt().isBefore(challenge.getStartDate())
			|| request.spentAt().isAfter(challenge.getEndDate())) {
			throw new BusinessException(ErrorCode.CERTIFICATION_SPENT_AT_OUT_OF_RANGE);
		}

		// 일일 인증 횟수 제한 검증 (spentAt 날짜가 변경된 경우, 자기 자신 제외)
		LocalDate spentDate = request.spentAt().toLocalDate();
		LocalDate originalDate = certification.getSpentAt().toLocalDate();
		if (!spentDate.equals(originalDate)) {
			LocalDateTime startOfDay = spentDate.atStartOfDay();
			LocalDateTime startOfNextDay = spentDate.plusDays(1).atStartOfDay();
			long dayCount = certificationRepository.countByUserAndChallengeAndSpentAtRange(
				user, challenge, startOfDay, startOfNextDay);
			if (dayCount >= DAILY_CERT_LIMIT) {
				throw new BusinessException(ErrorCode.CERTIFICATION_DAILY_LIMIT_EXCEEDED);
			}
		}

		// 챌린지에 설정된 카테고리 검증
		ChallengeCategory challengeCategory = challengeCategoryRepository
			.findByChallengeAndCategory_CategoryId(challenge, request.categoryId())
			.orElseThrow(() -> new BusinessException(ErrorCode.CERTIFICATION_CATEGORY_NOT_IN_CHALLENGE));

		// 지출 금액 및 절약 금액 계산
		int spendAmount = resolveSpendAmount(request);
		int newSavedAmount = challengeCategory.getAmount() - spendAmount;

		// 주차 절약 금액 보정 (비관적 잠금, 기존 절약 금액 차감 → 새 절약 금액 누적)
		ChallengeWeek currentWeek = challengeWeekRepository
			.findByChallengeAndWeekNumberForUpdate(challenge, certification.getChallengeWeek().getWeekNumber())
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		currentWeek.subtractSavedAmount(certification.getSavedAmount());
		currentWeek.addSavedAmount(newSavedAmount);

		// 이미지 처리: 새 이미지가 있으면 교체, deleteImage=true면 기존 이미지만 삭제
		String imageUrl = certification.getImageUrl();
		if (image != null && !image.isEmpty()) {
			String oldImageUrl = imageUrl;
			imageUrl = imageUploadService.upload(image, CERTIFICATION_FILE_NAME);
			if (oldImageUrl != null) {
				imageUploadService.deleteImageAfterCommit(oldImageUrl);
			}
		} else if (deleteImage && imageUrl != null) {
			imageUploadService.deleteImageAfterCommit(imageUrl);
			imageUrl = null;
		}

		// 인증 업데이트
		Category category = challengeCategory.getCategory();
		certification.update(
			category,
			request.spendType(),
			spendAmount,
			newSavedAmount,
			request.memo() != null ? request.memo() : "",
			imageUrl,
			request.spentAt()
		);

		log.info("[인증 수정] userId={}, certificationId={}, newSavedAmount={}",
			userId, certificationId, newSavedAmount);

		return CreateCertificationResponse.from(certification);
	}

	/** 인증 삭제 **/
	@Transactional
	public void deleteCertification(Long userId, Long certificationId) {
		User user = findUser(userId);
		Certification certification = findCertification(certificationId);

		// 본인 인증 검증
		validateOwner(certification, user);

		Challenge challenge = certification.getChallenge();

		// 챌린지 진행 중 여부 검증
		if (challenge.getStatus() != ChallengeStatus.ONGOING) {
			throw new BusinessException(ErrorCode.CHALLENGE_NOT_ONGOING);
		}

		// 주차 절약 금액 차감 (비관적 잠금)
		ChallengeWeek currentWeek = challengeWeekRepository
			.findByChallengeAndWeekNumberForUpdate(challenge, certification.getChallengeWeek().getWeekNumber())
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		currentWeek.subtractSavedAmount(certification.getSavedAmount());

		// S3 이미지는 커밋 후 삭제
		imageUploadService.deleteImageAfterCommit(certification.getImageUrl());

		// 좋아요 삭제 후 인증 삭제
		certificationLikeRepository.deleteByCertification(certification);
		certificationRepository.delete(certification);

		log.info("[인증 삭제] userId={}, certificationId={}, challengeId={}",
			userId, certificationId, certification.getChallenge().getChallengeId());
	}

	private int resolveSpendAmount(CreateCertificationRequest request) {
		if (request.spendType() == SpendType.NO_SPEND) {
			return 0;
		}
		if (request.spentAmount() == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "지출 인증 시 소비 금액은 필수입니다.");
		}
		return request.spentAmount();
	}

	private User findUser(Long userId) {
		return userRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
	}

	private Challenge findChallenge(Long challengeId) {
		return challengeRepository.findById(challengeId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));
	}

	private Certification findCertification(Long certificationId) {
		return certificationRepository.findById(certificationId)
			.orElseThrow(() -> new BusinessException(ErrorCode.CERTIFICATION_NOT_FOUND));
	}

	private void validateOwner(Certification certification, User user) {
		if (!certification.getUser().getUserId().equals(user.getUserId())) {
			throw new BusinessException(ErrorCode.CERTIFICATION_NOT_OWNER);
		}
	}
}
