package com.jjanpot.server.domain.auth.service;

import java.time.LocalDateTime;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.jjanpot.server.domain.auth.client.OAuthClient;
import com.jjanpot.server.domain.auth.client.OAuthClientRegistry;
import com.jjanpot.server.domain.auth.dto.DevOAuthUser;
import com.jjanpot.server.domain.auth.dto.LoginUserInfo;
import com.jjanpot.server.domain.auth.dto.OAuthUser;
import com.jjanpot.server.domain.auth.dto.UserCreateResult;
import com.jjanpot.server.domain.auth.dto.request.LoginRequest;
import com.jjanpot.server.domain.auth.dto.response.LoginResponse;
import com.jjanpot.server.domain.auth.dto.response.RefreshResponse;
import com.jjanpot.server.domain.auth.entity.RefreshToken;
import com.jjanpot.server.domain.auth.repository.RefreshTokenRepository;
import com.jjanpot.server.domain.user.entity.Provider;
import com.jjanpot.server.domain.user.entity.User;
import com.jjanpot.server.domain.user.entity.UserDevice;
import com.jjanpot.server.domain.user.entity.UserNotificationSetting;
import com.jjanpot.server.domain.user.repository.UserAgreementRepository;
import com.jjanpot.server.domain.user.repository.UserDeviceRepository;
import com.jjanpot.server.domain.user.repository.UserNotificationSettingRepository;
import com.jjanpot.server.domain.user.repository.UserRepository;
import com.jjanpot.server.global.auth.oauth.OAuthProperties;
import com.jjanpot.server.global.config.AuthProperties;
import com.jjanpot.server.global.exception.BusinessException;
import com.jjanpot.server.global.exception.ErrorCode;
import com.jjanpot.server.global.security.jwt.JwtTokenProvider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Service
public class AuthService {

	private final OAuthClientRegistry oAuthClientRegistry;
	private final OAuthProperties oAuthProperties;
	private final UserRepository userRepository;
	private final JwtTokenProvider jwtTokenProvider;
	private final RefreshTokenRepository refreshTokenRepository;
	private final AuthProperties authProperties;
	private final UserDeviceRepository userDeviceRepository;
	private final UserAgreementRepository userAgreementRepository;
	private final UserNotificationSettingRepository userNotificationSettingRepository;

	@Transactional
	public LoginResponse login(Provider provider, LoginRequest loginRequest) {
		OAuthClient oauthClient = oAuthClientRegistry.getAuthClient(provider);
		OAuthUser oauthUserInfo = oauthClient.getUserInfo(loginRequest.accessToken());

		UserCreateResult userCreateResult = findOrCreateUser(provider, oauthUserInfo);
		User user = userCreateResult.user();
		user.updateLastLoginAt();

		String accessToken = jwtTokenProvider.generateToken(user.getUserId());
		String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUserId());
		String fcmToken = loginRequest.fcmToken();
		String deviceUuid = loginRequest.deviceUuid();

		LocalDateTime expiresAt = LocalDateTime.now().plusDays(authProperties.getRefreshTokenExpiresDays());

		refreshTokenRepository.findByUser(user)
			.ifPresentOrElse(
				token -> updateExistingToken(token, refreshToken, expiresAt),
				() -> createNewToken(user, refreshToken, expiresAt)
			);

		if (StringUtils.hasText(fcmToken)) {
			userDeviceRepository.findByFcmToken(fcmToken)
				.ifPresentOrElse(
			device -> device.update(user, deviceUuid, fcmToken), // 기존 기기 정보 갱신
					() -> userDeviceRepository.save(UserDevice.create(user, deviceUuid, fcmToken))
				);
		} else {
			// FCM 토큰이 없다면 기기 정보 저장하지 않음
			log.warn("FCM Token is missing for user: {}", user.getUserId());
			// userDeviceRepository.findByFcmToken(fcmToken)
			// 	.ifPresent(
			// 	device -> {
			// 		if (device.getUser().getUserId().equals(user.getUserId())
			// 			&& device.getDeviceUuid().equals(loginRequest.deviceUuid())
			// 			&& device.isActive()) {
			// 			return;
			// 		}
			// 		device.update(user, loginRequest.deviceUuid(), fcmToken);
			// 	}
			// );
		}

		return createLoginResponse(user, accessToken, refreshToken);
	}

	@Transactional
	public LoginResponse devLogin(
		Provider provider,
		String providerId,
		String nickname,
		String email,
		String profileImageUrl
	) {
		return userRepository.findByProviderAndProviderId(provider, providerId)
			.map(this::issueLoginResponse)
			.orElseGet(() -> {
				User user = createUser(provider,
					new DevOAuthUser(provider, providerId, nickname, email, profileImageUrl));
				return issueLoginResponse(user);
			});
	}

	@Transactional
	public LoginResponse loginWithCode(String code, String redirectUri, String deviceUuid, String fcmToken) {
		String kakaoAccessToken = exchangeCodeForToken(code, redirectUri);
		return login(Provider.KAKAO, new LoginRequest(kakaoAccessToken, deviceUuid, fcmToken));
	}

	private String exchangeCodeForToken(String code, String redirectUri) {
		try {
			var tokenUrl = oAuthProperties.getKakao().getTokenUrl();
			var clientId = oAuthProperties.getKakao().getClientId();
			var clientSecret = oAuthProperties.getKakao().getClientSecret();

			var params = new java.util.LinkedHashMap<String, String>();
			params.put("grant_type", "authorization_code");
			params.put("client_id", clientId);
			params.put("redirect_uri", redirectUri);
			params.put("code", code);
			if (clientSecret != null && !clientSecret.isBlank()) {
				params.put("client_secret", clientSecret);
			}

			String body = params.entrySet().stream()
				.map(e -> java.net.URLEncoder.encode(e.getKey(), java.nio.charset.StandardCharsets.UTF_8)
					+ "=" + java.net.URLEncoder.encode(e.getValue(), java.nio.charset.StandardCharsets.UTF_8))
				.collect(java.util.stream.Collectors.joining("&"));

			var request = java.net.http.HttpRequest.newBuilder()
				.uri(java.net.URI.create(tokenUrl))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
				.build();

			var response = java.net.http.HttpClient.newHttpClient()
				.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

			var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
			var jsonNode = objectMapper.readTree(response.body());

			if (jsonNode.has("access_token")) {
				return jsonNode.get("access_token").asText();
			}

			log.error("카카오 토큰 교환 실패: {}", response.body());
			throw new BusinessException(ErrorCode.KAKAO_API_CALL_FAILED);
		} catch (BusinessException e) {
			throw e;
		} catch (Exception e) {
			log.error("카카오 토큰 교환 중 오류", e);
			throw new BusinessException(ErrorCode.KAKAO_API_CALL_FAILED);
		}
	}

	@Transactional
	public void logout(Long userId) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		refreshTokenRepository.deleteByUser(user);
		userDeviceRepository.findAllByUser(user)
			.forEach(UserDevice::deactivate);
	}

	@Transactional
	public RefreshResponse refreshToken(String token) {
		RefreshToken refreshToken = findRefreshToken(token);
		validateRefreshToken(refreshToken);

		User user = refreshToken.getUser();
		String newAccessToken = jwtTokenProvider.generateToken(user.getUserId());

		if (isRefreshTokenExpiringSoon(refreshToken)) {
			String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getUserId());
			updateRefreshToken(refreshToken, newRefreshToken);
			log.info("만료 임박 리프레시 토큰 갱신");

			return RefreshResponse.of(
				user.getUserId(),
				newAccessToken,
				newRefreshToken
			);

		}
		return RefreshResponse.of(
			user.getUserId(),
			newAccessToken,
			refreshToken.getToken()
		);
	}

	private UserCreateResult findOrCreateUser(Provider provider, OAuthUser oauthUser) {
		return userRepository
			.findByProviderAndProviderId(provider, oauthUser.getProviderId())
			.map(user -> new UserCreateResult(user, false))
			.orElseGet(() -> new UserCreateResult(createUser(provider, oauthUser), true));
	}

	private User createUser(Provider provider, OAuthUser oauthUser) {
		User user = User.create(
			provider,
			oauthUser.getProviderId(),
			oauthUser.getNickname(),
			oauthUser.getEmail(),
			oauthUser.getProfileImageUrl()
		);
		userRepository.save(user);
		userNotificationSettingRepository.save(UserNotificationSetting.defaultOf(user));
		return user;
	}

	private void updateExistingToken(RefreshToken token, String tokenValue, LocalDateTime expiresAt) {
		token.updateToken(tokenValue, expiresAt);
	}

	private void createNewToken(User user, String token, LocalDateTime expiresAt) {
		RefreshToken refreshToken = RefreshToken.createRefreshToken(user, token, expiresAt);
		refreshTokenRepository.save(refreshToken);
	}

	private RefreshToken findRefreshToken(String token) {
		return refreshTokenRepository.findByToken(token)
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
	}

	private void validateRefreshToken(RefreshToken refreshToken) {
		if (refreshToken.isExpired()) {
			refreshTokenRepository.delete(refreshToken);
			log.info("만료된 리프레시 토큰 삭제: {}", refreshToken.getId());
			throw new BusinessException(ErrorCode.EXPIRED_REFRESH_TOKEN);
		}
	}

	private boolean isRefreshTokenExpiringSoon(RefreshToken refreshToken) {
		LocalDateTime threshold = LocalDateTime.now().plusDays(authProperties.getRefreshTokenReIssueThresholdDays());
		return refreshToken.getExpiresAt().isBefore(threshold);
	}

	private void updateRefreshToken(RefreshToken refreshToken, String newRefreshToken) {
		LocalDateTime newExpireTime = LocalDateTime.now().plusDays(authProperties.getRefreshTokenExpiresDays());
		refreshToken.updateToken(newRefreshToken, newExpireTime);
	}

	private static final Set<String> REVIEW_ACCOUNT_EMAILS = Set.of(
		"jjanpot0220@gmail.com",
		"jjanpod.swyp4@gmail.com",
		"ueong1016@gmail.com",
		"n95g7hkhx9@privaterelay.appleid.com"
	);

	private boolean isReviewAccount(User user) {
		boolean isReview = REVIEW_ACCOUNT_EMAILS.contains(user.getEmail());

		if (isReview) {
			log.info("[ReviewMode] 심사 계정 로그인: userId={}, provider={}", user.getUserId(), user.getProvider());
		}
		return isReview;
	}

	private LoginResponse issueLoginResponse(User user) {
		user.updateLastLoginAt();

		String accessToken = jwtTokenProvider.generateToken(user.getUserId());
		String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUserId());
		LocalDateTime expiresAt = LocalDateTime.now().plusDays(authProperties.getRefreshTokenExpiresDays());

		refreshTokenRepository.findByUser(user)
			.ifPresentOrElse(
				token -> updateExistingToken(token, refreshToken, expiresAt),
				() -> createNewToken(user, refreshToken, expiresAt)
			);

		return createLoginResponse(user, accessToken, refreshToken);
	}

	private LoginResponse createLoginResponse(User user, String accessToken, String refreshToken) {
		boolean termsAgreed = userAgreementRepository.existsByUser(user);
		boolean onboardingCompleted = user.isOnboardingCompleted();

		return LoginResponse.of(
			accessToken,
			refreshToken,
			LoginUserInfo.from(user),
			!onboardingCompleted,
			termsAgreed,
			onboardingCompleted,
			isReviewAccount(user)
		);
	}
}
