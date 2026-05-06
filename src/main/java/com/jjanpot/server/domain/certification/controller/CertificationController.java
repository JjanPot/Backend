package com.jjanpot.server.domain.certification.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.jjanpot.server.domain.certification.controller.docs.CertificationControllerDocs;
import com.jjanpot.server.domain.certification.dto.request.CreateCertificationRequest;
import com.jjanpot.server.domain.certification.dto.response.CertificationFeedResponse;
import com.jjanpot.server.domain.certification.dto.response.CreateCertificationResponse;
import com.jjanpot.server.domain.certification.dto.response.ToggleLikeResponse;
import com.jjanpot.server.domain.certification.service.CertificationLikeService;
import com.jjanpot.server.domain.certification.service.CertificationService;
import com.jjanpot.server.global.annotation.CurrentUserId;
import com.jjanpot.server.global.common.dto.SuccessResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/certifications/v1")
public class CertificationController implements CertificationControllerDocs {

	private final CertificationService certificationService;
	private final CertificationLikeService certificationLikeService;

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public SuccessResponse<CreateCertificationResponse> createCertification(
		@CurrentUserId Long userId,
		@Valid @RequestPart("request") CreateCertificationRequest request,
		@RequestPart(value = "image", required = false) MultipartFile image
	) {
		return SuccessResponse.created(certificationService.createCertification(userId, request, image));
	}

	@PutMapping(value = "/{certificationId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public SuccessResponse<CreateCertificationResponse> updateCertification(
		@CurrentUserId Long userId,
		@PathVariable Long certificationId,
		@Valid @RequestPart("request") CreateCertificationRequest request,
		@RequestPart(value = "image", required = false) MultipartFile image,
		@RequestPart(value = "deleteImage", required = false) Boolean deleteImage
	) {
		return SuccessResponse.ok(
			certificationService.updateCertification(userId, certificationId, request, image, Boolean.TRUE.equals(deleteImage))
		);
	}

	@DeleteMapping("/{certificationId}")
	public SuccessResponse<Void> deleteCertification(
		@CurrentUserId Long userId,
		@PathVariable Long certificationId
	) {
		certificationService.deleteCertification(userId, certificationId);
		return SuccessResponse.noContent();
	}

	@GetMapping("/challenge/{challengeId}")
	public SuccessResponse<List<CertificationFeedResponse>> getCertificationFeed(
		@CurrentUserId Long userId,
		@PathVariable Long challengeId
	) {
		return SuccessResponse.ok(certificationService.getCertificationFeed(userId, challengeId));
	}

	@PostMapping("/{certificationId}/likes")
	public SuccessResponse<ToggleLikeResponse> toggleLike(
		@CurrentUserId Long userId,
		@PathVariable Long certificationId
	) {
		return SuccessResponse.ok(certificationLikeService.toggleLike(userId, certificationId));
	}
}
