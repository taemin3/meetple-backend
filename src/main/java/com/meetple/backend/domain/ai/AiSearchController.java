package com.meetple.backend.domain.ai;

import static com.meetple.backend.domain.ai.AiSearchContracts.*;
import com.meetple.backend.global.config.OpenApiConfig;
import com.meetple.backend.global.response.ApiResponse;
import com.meetple.backend.global.response.SuccessStatus;
import com.meetple.backend.global.security.AuthenticatedMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
public class AiSearchController {
    private final AiSearchService service;

    @PostMapping("/api/v1/meetings/ai-search")
    @Operation(summary = "자연어 모임 검색", description = "질문을 해석해 모임과 소개글 근거를 반환합니다. AI 설정 활성화가 필요합니다.")
    public ResponseEntity<ApiResponse<Response>> search(
            @AuthenticationPrincipal AuthenticatedMember member, @Valid @RequestBody Request request
    ) {
        return ApiResponse.success(SuccessStatus.OK, service.search(member.id(), request));
    }
}
