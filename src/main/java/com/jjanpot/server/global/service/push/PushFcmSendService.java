package com.jjanpot.server.global.service.push;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.google.api.core.ApiFuture;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.jjanpot.server.domain.notification.dto.FcmSendCommand;

import lombok.extern.slf4j.Slf4j;

/**
 * Firebase Cloud Messaging으로 푸시 알림을 실제 전송하는 서비스입니다.
 *
 * Notification 도메인은 발송 대상과 문구를 FcmSendCommand로 만들고,
 * 이 서비스는 해당 명령을 Firebase Message로 변환해 배치 전송합니다.
 */
@Slf4j
@Service
public class PushFcmSendService implements PushSendService {
	private final FirebaseMessaging firebaseMessaging;
	private final Executor fcmCallbackExecutor;

	@Value("${custom.fcm.dry:false}")
	private boolean isDryRun;

	public PushFcmSendService(
		FirebaseMessaging firebaseMessaging,
		@Qualifier("notificationExecutor") Executor fcmCallbackExecutor
	) {
		this.firebaseMessaging = firebaseMessaging;
		this.fcmCallbackExecutor = fcmCallbackExecutor;
	}

	/**
	 * 알림 발송
	 * @param fcmSendCommands 사용자 푸시 알림 메시지 전송 객체
	 * @return 비동기 결과
	 */
	@Async
	@Override
	public CompletableFuture<List<FcmSendResult>> sendMessage(List<FcmSendCommand> fcmSendCommands) {
		List<Message> messageList = fcmSendCommands.stream()
			.map(this::toMessage)
			.toList();

		ApiFuture<BatchResponse> future = firebaseMessaging.sendEachAsync(messageList, isDryRun);

		CompletableFuture<List<FcmSendResult>> completableFuture = new CompletableFuture<>();

		// 콜백 리스너 등록
		future.addListener(() -> {
			try {
				BatchResponse response = future.get();
				List<FcmSendResult> results = response.getResponses().stream()
					.map(res -> {
						if (res.isSuccessful()) {
							return FcmSendResult.success(res.getMessageId());
						} else {
							log.error("FCM Send Failed: {}", res.getException().getMessage(), res.getException());
							// 실패시 FCM 에러 코드 추출 (UNREGISTERED, INVALID_ARGUMENT)
							String errorCode = res.getException().getMessagingErrorCode().name();
							return FcmSendResult.fail(errorCode, res.getException().getMessage());
						}
					}).toList();
				completableFuture.complete(results);
			} catch (Exception e) {
				completableFuture.completeExceptionally(e);
			}
		}, fcmCallbackExecutor);

		return completableFuture;
	}

	/**
	 * FcmSendCommand를 Firebase Message로 변환합니다.
	 *
	 * type/relateId는 기존 앱 호환을 위해 기본 data로 유지하고,
	 * command.data()에 담긴 소셜 알림용 challengeId, certificationId, deepLink, utm 값을 추가 병합합니다.
	 */
	private Message toMessage(FcmSendCommand command) {
		Map<String, String> data = new LinkedHashMap<>();
		data.put("type", command.type().getCode());
		if (command.relatedId() != null) {
			data.put("relateId", String.valueOf(command.relatedId()));
		}
		if (command.data() != null) {
			command.data().forEach((key, value) -> {
				if (key != null && value != null) {
					data.put(key, value);
				}
			});
		}

		return Message.builder()
			.setToken(command.targetToken())
			.setNotification(Notification.builder().setTitle(command.title()).setBody(command.body()).build())
			.putAllData(data)
			.build();
	}
}
