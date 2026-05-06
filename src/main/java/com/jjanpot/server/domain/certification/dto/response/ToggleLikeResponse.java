package com.jjanpot.server.domain.certification.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "좋아요 토글 응답")
public record ToggleLikeResponse(

    @Schema(description = "좋아요 활성 여부 (true: 좋아요, false: 취소)", example = "true")
    boolean isLiked,

    @Schema(description = "현재 좋아요 수", example = "3")
    int likeCount
) {
}
