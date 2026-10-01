package com.meetple.backend.domain.moderation.analysis;

import static com.meetple.backend.domain.moderation.analysis.ReportAnalysisContracts.*;

import com.meetple.backend.domain.moderation.policy.AiModerationAuthenticator;
import com.meetple.backend.global.response.ApiResponse;
import com.meetple.backend.global.response.SuccessStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/ai/moderation")
public class ReportAnalysisToolController {
    private final AiModerationAuthenticator authenticator;
    private final ReportAnalysisService service;

    @GetMapping("/reports/{reportId}/context")
    public ResponseEntity<ApiResponse<Context>> getContext(
            @RequestHeader(value = "X-AI-Service-Token", required = false) String serviceToken,
            @PathVariable long reportId
    ) {
        authenticator.verify(serviceToken);
        return ApiResponse.success(SuccessStatus.OK, service.getContext(reportId));
    }

    @PostMapping("/policies/search-for-report")
    public ResponseEntity<ApiResponse<AuditedPolicyCandidates>> searchPolicies(
            @RequestHeader(value = "X-AI-Service-Token", required = false) String serviceToken,
            @Valid @RequestBody AuditedPolicySearchRequest request
    ) {
        authenticator.verify(serviceToken);
        return ApiResponse.success(SuccessStatus.OK, service.searchPolicies(request));
    }

    @PutMapping("/reports/{reportId}/analysis")
    public ResponseEntity<ApiResponse<Completion>> complete(
            @RequestHeader(value = "X-AI-Service-Token", required = false) String serviceToken,
            @PathVariable long reportId,
            @Valid @RequestBody CompleteRequest request
    ) {
        authenticator.verify(serviceToken);
        return ApiResponse.success(SuccessStatus.OK, service.complete(reportId, request));
    }

    @PutMapping("/reports/{reportId}/analysis/failure")
    public ResponseEntity<ApiResponse<Void>> fail(
            @RequestHeader(value = "X-AI-Service-Token", required = false) String serviceToken,
            @PathVariable long reportId,
            @Valid @RequestBody FailureRequest request
    ) {
        authenticator.verify(serviceToken);
        service.fail(reportId, request);
        return ApiResponse.successOnly(SuccessStatus.OK);
    }
}
