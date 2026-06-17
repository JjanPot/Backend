package com.jjanpot.server.domain.user.controller.docs;

import com.jjanpot.server.domain.user.dto.request.NotificationSettingUpdateRequest;
import com.jjanpot.server.domain.user.dto.response.NotificationSettingResponse;
import com.jjanpot.server.global.common.dto.SuccessResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User - Notification", description = "알림 설정 API")
@SecurityRequirement(name = "JWT TOKEN")
public interface UserNotificationSettingControllerDocs {

	@Operation(summary = "알림 설정 조회", description = "1일 1회 미인증 알림, 주간 미인증 알림, 소셜 알림, 마케팅 수신 동의 여부를 조회합니다.")
	@ApiResponse(responseCode = "200", description = "알림 설정 조회 성공")
	SuccessResponse<NotificationSettingResponse> getNotification(@Parameter(hidden = true) Long userId);

	@Operation(summary = "알림 설정 수정", description = "1일 1회 미인증 알림, 주간 미인증 알림, 소셜 알림, 마케팅 수신 동의 여부를 수정합니다. socialEnabled 미입력 시 기존 값을 유지합니다.")
	@ApiResponse(responseCode = "200", description = "알림 설정 수정 성공")
	SuccessResponse<Void> updateNotification(
		NotificationSettingUpdateRequest request,
		@Parameter(hidden = true) Long userId
	);
}
