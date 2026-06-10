package com.jjanpot.server.domain.user.service;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jjanpot.server.domain.auth.repository.RefreshTokenRepository;
import com.jjanpot.server.domain.block.repository.BlockRepository;
import com.jjanpot.server.domain.certification.repository.CertificationLikeRepository;
import com.jjanpot.server.domain.certification.repository.CertificationRepository;
import com.jjanpot.server.domain.challenge.dto.ChallengeStatsDto;
import com.jjanpot.server.domain.challenge.entity.Challenge;
import com.jjanpot.server.domain.challenge.entity.ChallengeStatus;
import com.jjanpot.server.domain.challenge.repository.ChallengeMemberResultRepository;
import com.jjanpot.server.domain.challenge.repository.ChallengeRepository;
import com.jjanpot.server.domain.notification.repository.NotificationRepository;
import com.jjanpot.server.domain.report.repository.ReportRepository;
import com.jjanpot.server.domain.team.entity.Team;
import com.jjanpot.server.domain.team.entity.TeamMembers;
import com.jjanpot.server.domain.team.repository.TeamMembersRepository;
import com.jjanpot.server.domain.team.repository.TeamRepository;
import com.jjanpot.server.domain.user.dto.request.NotificationSettingUpdateRequest;
import com.jjanpot.server.domain.user.dto.request.ProfileCreateRequest;
import com.jjanpot.server.domain.user.dto.request.ProfileUpdateRequest;
import com.jjanpot.server.domain.user.dto.request.UserAgreementRequest;
import com.jjanpot.server.domain.user.dto.response.ChallengeStatsResponse;
import com.jjanpot.server.domain.user.dto.response.InviteCodeResponse;
import com.jjanpot.server.domain.user.dto.response.NotificationSettingResponse;
import com.jjanpot.server.domain.user.dto.response.ProfileCreateResponse;
import com.jjanpot.server.domain.user.dto.response.UserProfileResponse;
import com.jjanpot.server.domain.user.entity.User;
import com.jjanpot.server.domain.user.entity.UserAgreement;
import com.jjanpot.server.domain.user.entity.UserNotificationSetting;
import com.jjanpot.server.domain.user.repository.UserAgreementRepository;
import com.jjanpot.server.domain.user.repository.UserDeviceRepository;
import com.jjanpot.server.domain.user.repository.UserNotificationSettingRepository;
import com.jjanpot.server.domain.user.repository.UserRepository;
import com.jjanpot.server.global.common.service.ImageUploadService;
import com.jjanpot.server.global.exception.BusinessException;
import com.jjanpot.server.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class UserService {

	private static final String PROFILE_FILE_NAME = "profile/";
	private static final String DEFAULT_PROFILE_IMAGE = "https://jjanpot-s3-bucket.s3.ap-northeast-2.amazonaws.com/images/profile/defaultImg.png";

	private final UserRepository userRepository;
	private final TeamRepository teamRepository;
	private final TeamMembersRepository teamMembersRepository;
	private final ChallengeRepository challengeRepository;
	private final ChallengeMemberResultRepository challengeMemberResultRepository;
	private final UserAgreementRepository userAgreementRepository;
	private final UserDeviceRepository userDeviceRepository;
	private final UserNotificationSettingRepository userNotificationSettingRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final CertificationLikeRepository certificationLikeRepository;
	private final CertificationRepository certificationRepository;
	private final NotificationRepository notificationRepository;
	private final ReportRepository reportRepository;
	private final BlockRepository blockRepository;
	private final ImageUploadService imageUploadService;

	//프로필 생성
	@Transactional
	public ProfileCreateResponse onboardingCreateProfile(ProfileCreateRequest request, Long userId) {
		User user = getUserByUserId(userId);

		String imageUrl = request.profileImageUrl();
		if (imageUrl == null || imageUrl.isBlank()) {
			imageUrl = DEFAULT_PROFILE_IMAGE;
		}

		user.updateOnboarding(
			imageUrl,
			request.nickname(),
			request.birthDate()
		);

		return ProfileCreateResponse.of(
			user.getProfileImageUrl(),
			user.getNickname(),
			user.getBirthDate()
		);
	}

	// 프로필 수정
	@Transactional
	public UserProfileResponse updateProfile(Long userId, ProfileUpdateRequest request) {
		User user = getUserByUserId(userId);

		String nickname = resolveProfileNickname(user, request.nickname());
		String imageUrl = resolveProfileImageUrl(user, request.profileImageUrl());
		LocalDate birthDate = request.birthDate() != null ? request.birthDate() : user.getBirthDate();

		user.updateProfile(
			imageUrl,
			nickname,
			birthDate
		);

		return UserProfileResponse.from(user);
	}

	// 초대코드 기반 팀 참여 (온보딩 흐름)
	@Transactional
	public InviteCodeResponse joinChallengeByInviteCode(String inviteCode, Long userId) {
		// 비관적 락으로 동일 사용자 동시 요청 직렬화
		User user = userRepository.findByIdForUpdate(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

		// 활성 챌린지 중복 참여 방지
		if (challengeRepository.existsActiveByUserIdAndStatusIn(
			userId, java.util.List.of(ChallengeStatus.WAITING, ChallengeStatus.ONGOING))) {
			throw new BusinessException(ErrorCode.CHALLENGE_ALREADY_ACTIVE);
		}

		Team team = teamRepository.findByInviteCode(inviteCode)
			.orElseThrow(() -> new BusinessException(ErrorCode.TEAM_NOT_FOUND));
		Challenge challenge = challengeRepository.findByTeamAndStatus(team, ChallengeStatus.WAITING)
			.orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_JOINABLE));
		if (teamMembersRepository.existsByTeamAndUser(team, user)) {
			throw new BusinessException(ErrorCode.ALREADY_TEAM_MEMBER);
		}
		if (team.getCurrentMemberCount() >= team.getMaxMemberCount()) {
			throw new BusinessException(ErrorCode.TEAM_ALREADY_FULL);
		}
		teamMembersRepository.save(TeamMembers.ofMember(team, user));
		team.increaseMemberCount();

		log.info("[초대코드 참여] userId={}, teamId={}, challengeId={}, inviteCode={}",
			userId, team.getTeamId(), challenge.getChallengeId(), inviteCode);

		return InviteCodeResponse.from(team, challenge);
	}

	//약관 동의
	@Transactional
	public void agreeToTerms(Long userId, UserAgreementRequest request) {
		User user = getUserByUserId(userId);

		if (!Boolean.TRUE.equals(request.ageVerified())
			|| !Boolean.TRUE.equals(request.termsOfServiceAgreed())
			|| !Boolean.TRUE.equals(request.privacyPolicyAgreed())) {
			throw new BusinessException(ErrorCode.REQUIRED_AGREEMENT_MISSING);
		}

		// 마케팅 동의 알림 설정 테이블에 저장
		UserNotificationSetting setting = userNotificationSettingRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		setting.update(setting.isDailyEnabled(), setting.isWeeklyEnabled(),
			Boolean.TRUE.equals(request.marketingConsent()));

		if (userAgreementRepository.existsByUser(user)) {
			return;
		}

		userAgreementRepository.save(UserAgreement.from(
			request.ageVerified(),
			request.termsOfServiceAgreed(),
			request.privacyPolicyAgreed(),
			user
		));
	}

	/** 회원 탈퇴 **/
	@Transactional
	public void withdraw(Long userId) {
		User user = getUserByUserId(userId);

		// 진행 중인 챌린지가 있으면 탈퇴 차단
		if (challengeRepository.existsActiveByUserIdAndStatusIn(
			userId, java.util.List.of(ChallengeStatus.WAITING, ChallengeStatus.ONGOING))) {
			throw new BusinessException(ErrorCode.WITHDRAW_BLOCKED_ACTIVE_CHALLENGE);
		}

		// FK 자식 테이블부터 순서대로 삭제
		reportRepository.deleteAll(reportRepository.findAllByReporterOrReported(user, user));
		blockRepository.deleteAllByBlockerOrBlocked(user, user);
		certificationLikeRepository.deleteAllByUser(user);
		certificationLikeRepository.deleteByCertificationUser(user);
		certificationRepository.deleteAllByUser(user);
		challengeMemberResultRepository.deleteAllByUser(user);
		notificationRepository.deleteAllByUserId(userId);
		teamMembersRepository.deleteAllByUser(user);
		userDeviceRepository.deleteAllByUser(user);
		userNotificationSettingRepository.deleteById(userId);
		userAgreementRepository.deleteByUser(user);
		refreshTokenRepository.deleteByUser(user);
		userRepository.delete(user);

		log.info("[회원 탈퇴] userId={}", userId);
	}

	private User getUserByUserId(Long userId) {
		return userRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
	}

	public UserProfileResponse getProfile(Long userId) {
		User user = getUserByUserId(userId);
		return UserProfileResponse.from(user);
	}

	private String resolveProfileNickname(User user, String nickname) {
		if (nickname == null) {
			return user.getNickname();
		}
		if (nickname.isBlank()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "닉네임은 공백일 수 없습니다.");
		}
		return nickname;
	}

	private String resolveProfileImageUrl(User user, String profileImageUrl) {
		if (profileImageUrl == null) {
			return user.getProfileImageUrl();
		}
		if (profileImageUrl.isBlank()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "프로필 이미지 URL은 공백일 수 없습니다.");
		}
		return profileImageUrl;
	}

	public ChallengeStatsResponse getChallengeStats(Long userId) {
		User user = getUserByUserId(userId);
		ChallengeStatsDto dto = challengeMemberResultRepository.aggregateChallengeStatsByUser(
			user, ChallengeStatus.COMPLETED, ChallengeStatus.FAILED);
		return ChallengeStatsResponse.of(dto.getSuccessCount(), dto.getFailCount());
	}

	public NotificationSettingResponse getNotification(Long userId) {
		UserNotificationSetting setting = userNotificationSettingRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		return NotificationSettingResponse.of(setting);
	}

	@Transactional
	public void updateNotification(Long userId, NotificationSettingUpdateRequest request) {
		UserNotificationSetting setting = userNotificationSettingRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		setting.update(request.dailyEnabled(), request.weeklyEnabled(), request.marketingConsent());
	}
}
