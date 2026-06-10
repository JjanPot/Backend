package com.jjanpot.server.domain.user.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.jjanpot.server.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "users",
	uniqueConstraints = {
		@UniqueConstraint(columnNames = {"provider", "provider_id"})
	},
	indexes = {
		@Index(name = "idx_provider", columnList = "provider, provider_id"),
		@Index(name = "idx_email", columnList = "email")
	}
)
public class User extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(nullable = false, length = 50)
	private String nickname;

	private String email;

	@Column(length = 1024)
	private String profileImageUrl;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Provider provider;

	@Column(name = "provider_id", nullable = false)
	private String providerId;

	@Builder.Default
	@Column(name = "last_login_at", nullable = false)
	private LocalDateTime lastLoginAt = LocalDateTime.now();

	@Builder.Default
	@Column(name = "onboarding_completed", nullable = false, columnDefinition = "TINYINT(1)")
	private boolean onboardingCompleted = false;

	@Column(name = "birth_date")
	private LocalDate birthDate;

	public static User create(
		Provider provider,
		String providerId,
		String nickname,
		String email,
		String profileImageUrl
	) {
		return User.builder()
			.provider(provider)
			.providerId(providerId)
			.nickname(nickname)
			.email(email)
			.profileImageUrl(profileImageUrl)
			.build();
	}

	public void updateLastLoginAt() {
		this.lastLoginAt = LocalDateTime.now();
	}

	public void updateOnboarding(String profileImageUrl, String nickname, LocalDate birthDate) {
		this.profileImageUrl = profileImageUrl;
		this.nickname = nickname;
		this.birthDate = birthDate;
		this.onboardingCompleted = true;
	}

	public void updateProfile(String profileImageUrl, String nickname, LocalDate birthDate) {
		this.profileImageUrl = profileImageUrl;
		this.nickname = nickname;
		this.birthDate = birthDate;
	}
}
