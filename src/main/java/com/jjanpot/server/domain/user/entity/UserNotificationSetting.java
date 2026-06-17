package com.jjanpot.server.domain.user.entity;

import com.jjanpot.server.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_notification_setting")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserNotificationSetting extends BaseEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "daily_enabled", nullable = false, columnDefinition = "TINYINT(1)")
    private boolean dailyEnabled = true;

    @Column(name = "weekly_enabled", nullable = false, columnDefinition = "TINYINT(1)")
    private boolean weeklyEnabled = true;

    @Column(name = "social_enabled", nullable = false, columnDefinition = "TINYINT(1)")
    private boolean socialEnabled = true;

    @Column(name = "marketing_consent_enabled", nullable = false, columnDefinition = "TINYINT(1)")
    private boolean marketingConsentEnabled = false;

    public static UserNotificationSetting defaultOf(User user) {
        UserNotificationSetting setting = new UserNotificationSetting();
        setting.user = user;
        setting.dailyEnabled = true;
        setting.weeklyEnabled = true;
        setting.socialEnabled = true;
        setting.marketingConsentEnabled = false;
        return setting;
    }

    public void update(boolean dailyEnabled, boolean weeklyEnabled, boolean socialEnabled, boolean marketingConsentEnabled) {
        this.dailyEnabled = dailyEnabled;
        this.weeklyEnabled = weeklyEnabled;
        this.socialEnabled = socialEnabled;
        this.marketingConsentEnabled = marketingConsentEnabled;
    }
}
