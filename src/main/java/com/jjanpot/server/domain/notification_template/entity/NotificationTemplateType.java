package com.jjanpot.server.domain.notification_template.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.jjanpot.server.global.util.CodeEnum;
import com.jjanpot.server.global.util.EnumUtils;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum NotificationTemplateType implements CodeEnum {
	ENCOURAGE("ENCOURAGE", "인증 독려"),
	CERTIFICATION_CREATED("CERTIFICATION_CREATED", "팀원 신규 인증"),
	LIKE("LIKE", "좋아요"),
	// GOAL_NEAR,      // 목표 금액 10% 이하 남음
	GOAL_COMPLETE("GOAL_COMPLETE", "목표 달성")
	;

	private final String code;
	private final String description;

	@JsonValue
	public String toJson() {
		return getCode();
	}

	@JsonCreator
	public static NotificationTemplateType from(String code) {
		return EnumUtils.fromCode(NotificationTemplateType.class, code);
	}

	@Override
	public String getCode() {
		return code;
	}

	@Override
	public String getDescription() {
		return description;
	}
}
