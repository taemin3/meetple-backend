package com.meetple.backend.domain.image.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ImageUploadUrlsRequest(
        @NotEmpty(message = "이미지 정보를 입력해주세요.")
        @Size(max = 10, message = "이미지는 최대 10장까지 업로드할 수 있습니다.")
        List<@NotNull(message = "이미지 정보를 입력해주세요.") @Valid ImageUploadUrlRequest> images
) {
}
