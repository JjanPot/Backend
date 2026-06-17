package com.jjanpot.server.domain.notification_template.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jjanpot.server.domain.notification_template.dto.CreateNotificationTemplateRequest;
import com.jjanpot.server.domain.notification_template.service.NotificationTemplateService;
import com.jjanpot.server.global.annotation.CurrentUserId;
import com.jjanpot.server.global.common.dto.SuccessResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Notification Template", description = "알림 템플릿 관리 API")
@SecurityRequirement(name = "bearerAuth")
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/notification/template/v1")
@RestController
public class NotificationTemplateController {
	private final NotificationTemplateService notificationTemplateService;

	@Operation(
		summary = "알림 템플릿 생성",
		description = """
			푸시 알림 발송 시 사용할 제목/본문 템플릿을 생성합니다.

			## 소셜 알림 템플릿
			- `CERTIFICATION_CREATED`: 팀원 신규 인증 알림
			- `LIKE`: 내 인증 게시물 좋아요 알림

			## 닉네임 변수
			- 제목/본문에 `{nickname}`을 포함하면 푸시 발송 시 액션을 수행한 유저 닉네임으로 치환됩니다.
			- 예: `{nickname}님이 인증을 올렸어요 ✅` → `민지님이 인증을 올렸어요 ✅`

			## 참고
			- 기본 소셜 알림 템플릿은 Flyway 마이그레이션으로 자동 추가됩니다.
			- 이 API는 운영/테스트 중 템플릿을 수동 추가해야 할 때 사용합니다.
			"""
	)
	@ApiResponse(responseCode = "201", description = "알림 템플릿 생성 성공")
	@PostMapping
	public SuccessResponse<Long> create(
		@CurrentUserId Long userId,
		@Valid @RequestBody CreateNotificationTemplateRequest createNotificationTemplateRequest
	) {
		Long notificationTemplateId = notificationTemplateService.create(userId, createNotificationTemplateRequest);

		return SuccessResponse.created(notificationTemplateId);
	}
}
