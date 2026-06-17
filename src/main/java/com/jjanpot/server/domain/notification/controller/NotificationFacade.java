package com.jjanpot.server.domain.notification.controller;

import org.springframework.web.bind.annotation.PathVariable;

import com.jjanpot.server.global.common.dto.ErrorResponse;
import com.jjanpot.server.global.common.dto.SuccessResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Notification", description = "알림 관련 API")
@SecurityRequirement(name = "bearerAuth")
public interface NotificationFacade {
	@Operation(
		summary = "알림 읽음 처리",
		description = "사용자가 특정 알림을 확인했을 때 읽음 상태로 변경합니다."
	)
	@ApiResponses(value = {
		@ApiResponse(
			responseCode = "200",
			description = "읽음 처리 성공",
			content = @Content(schema = @Schema(implementation = SuccessResponse.class))
		),
		@ApiResponse(
			responseCode = "401",
			description = "해당 사용자의 알림이 아님",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class))
		),
		@ApiResponse(
			responseCode = "404",
			description = "존재하지 않는 알림",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class))
		)

	})
	SuccessResponse<Long> read(
		@Parameter(hidden = true) Long userId,
		@Parameter(
			description = "읽음 처리할 알림의 고유 ID",
			required = true,
			example = "101"
		)
		@PathVariable Long notificationId
	);

	@Operation(
		summary = "[TEST] 스케줄 기반 푸시 알림 설명",
		description = """
			해당 알림은 서버에서 스케줄러(@Scheduled)에 의해 자동 발송되기 때문에 테스트 할 수 없습니다.
			
			■ 발송 주기
			- 매일 18:00
			- 테스트를 위해 test flight에서는 호출 하면 발송하게 만듦
			
			■ FCM Payload 구조
			[Data]
			- type: 알림 타입
			- relateId: 연관 리소스 ID (nullable, 기존 호환 필드)
			- challengeId: 챌린지 ID (소셜 알림)
			- certificationId: 인증 게시물 ID (소셜 알림)
			- deepLink: 앱 이동용 딥링크 (소셜 알림)
			- utm_source: push_notification (소셜 알림)
			- utm_campaign: social_interaction (소셜 알림)
			
			■ type 값 목록
			- ENCOURAGE: 인증 독려
			- CERTIFICATION_CREATED: 팀원 신규 인증 알림
			- LIKE: 좋아요 알림
			- GOAL_COMPLETE: 목표 달성
			
			■ relateId 정책
			- type에 따라 값이 다름
			  - ENCOURAGE : 챌린지 ID
			  - CERTIFICATION_CREATED : 인증 ID
			  - LIKE : 인증 ID
			  - GOAL_COMPLETE : 미정
			- null 값이 내려올 수 있기 때문에 앱에서 null 체크 후 처리 필요
			"""
	)
	void dailyPushNotification();

	@Operation(
		summary = "[TEST] 스케줄 기반 주간 푸시 알림 설명",
		description = """
			해당 알림은 서버에서 스케줄러(@Scheduled)에 의해 자동 발송되기 때문에 실제 사용하지 않음.
			하지만 2분마다 도는건 너무 많아서 호출하면 요청되게 변경
			
			■ 발송 주기
			- 챌린지 시작 후 매주 3,5,7일 20:00
			
			■ FCM Payload 구조
			[Data]
			- type: 알림 타입
			- relateId: 연관 리소스 ID (nullable, 기존 호환 필드)
			- challengeId: 챌린지 ID (소셜 알림)
			- certificationId: 인증 게시물 ID (소셜 알림)
			- deepLink: 앱 이동용 딥링크 (소셜 알림)
			- utm_source: push_notification (소셜 알림)
			- utm_campaign: social_interaction (소셜 알림)
			
			■ type 값 목록
			- ENCOURAGE: 인증 독려
			- CERTIFICATION_CREATED: 팀원 신규 인증 알림
			- LIKE: 좋아요 알림
			- GOAL_COMPLETE: 목표 달성
			
			■ relateId 정책
			- type에 따라 값이 다름
			  - ENCOURAGE : 챌린지 ID
			  - CERTIFICATION_CREATED : 인증 ID
			  - LIKE : 인증 ID
			  - GOAL_COMPLETE : 미정
			- null 값이 내려올 수 있기 때문에 앱에서 null 체크 후 처리 필요
			"""
	)
	void weeklyPushNotification();
}
