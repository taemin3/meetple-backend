package com.meetple.backend.domain.moderation.policy;

import static com.meetple.backend.domain.moderation.policy.ModerationPolicyContracts.*;

import com.meetple.backend.global.response.ApiResponse;
import com.meetple.backend.global.response.SuccessStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/ai/moderation/policies")
public class ModerationPolicyToolController {
    private final AiModerationAuthenticator authenticator;
    private final ModerationPolicyService service;

    @PostMapping("/search")
    public ResponseEntity<ApiResponse<Candidates>> search(
            @RequestHeader(value = "X-AI-Service-Token", required = false) String serviceToken,
            @Valid @RequestBody SearchRequest request
    ) {
        authenticator.verify(serviceToken);
        return ApiResponse.success(SuccessStatus.OK, service.search(request));
    }

    @GetMapping("/embedding-jobs")
    public ResponseEntity<ApiResponse<EmbeddingJobs>> embeddingJobs(
            @RequestHeader(value = "X-AI-Service-Token", required = false) String serviceToken,
            @RequestParam String embeddingModel,
            @RequestParam(defaultValue = "50") int limit
    ) {
        authenticator.verify(serviceToken);
        return ApiResponse.success(
                SuccessStatus.OK,
                service.findEmbeddingJobs(embeddingModel, limit)
        );
    }

    @PutMapping("/chunks/{policyChunkId}/embedding")
    public ResponseEntity<ApiResponse<Void>> upsertEmbedding(
            @RequestHeader(value = "X-AI-Service-Token", required = false) String serviceToken,
            @PathVariable long policyChunkId,
            @Valid @RequestBody EmbeddingUpsertRequest request
    ) {
        authenticator.verify(serviceToken);
        service.upsertEmbedding(policyChunkId, request);
        return ApiResponse.successOnly(SuccessStatus.OK);
    }
}
