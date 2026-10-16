package com.jjanpot.server.global.common.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jjanpot.server.global.common.dto.PresignedUrlResponse;
import com.jjanpot.server.global.common.dto.SuccessResponse;
import com.jjanpot.server.global.common.service.ImageUploadService;
import com.jjanpot.server.global.infra.storage.FileUploader.PresignedUrlResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Image", description = "이미지 업로드 API")
@SecurityRequirement(name = "JWT TOKEN")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/images/v1")
public class ImageController {

	private final ImageUploadService imageUploadService;

	@Operation(
		summary = "Presigned URL 발급",
		description = """
			S3 이미지 업로드용 Presigned URL을 발급합니다.

			- `directory`: 업로드 경로 (profile/, certification/)
			- `contentType`: 이미지 타입 (image/jpeg, image/png, image/webp)
			- JWT 인증 필요: `Authorization: Bearer {accessToken}` 헤더를 포함해야 합니다.

			응답의 `uploadUrl`로 PUT 요청하여 이미지를 직접 업로드하고,
			`imageUrl`을 프로필 등록 등의 API에 전달하면 됩니다.

			URL 유효시간: 10분
			"""
	)
	@ApiResponse(responseCode = "200", description = "Presigned URL 발급 성공")
	@GetMapping("/presigned-url")
	public SuccessResponse<PresignedUrlResponse> getPresignedUrl(
		@Parameter(description = "업로드 경로 (profile/, certification/)", example = "profile/")
		@RequestParam String directory,

		@Parameter(description = "이미지 Content-Type", example = "image/jpeg")
		@RequestParam String contentType
	) {
		PresignedUrlResult result = imageUploadService.generatePresignedUrl(directory, contentType);
		return SuccessResponse.ok(PresignedUrlResponse.from(result));
	}
}
