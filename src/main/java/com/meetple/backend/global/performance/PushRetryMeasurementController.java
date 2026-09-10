package com.meetple.backend.global.performance;

import com.meetple.backend.global.response.ApiResponse;
import com.meetple.backend.global.response.SuccessStatus;
import com.meetple.backend.global.security.AuthenticatedMember;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/performance/push-retry")
@ConditionalOnProperty(
        prefix = "meetple.performance.push-retry",
        name = "enabled",
        havingValue = "true"
)
public class PushRetryMeasurementController {

    private final PushRetryMeasurementService service;

    @PostMapping("/events")
    public ResponseEntity<ApiResponse<EventCreated>> create(
            @AuthenticationPrincipal AuthenticatedMember member,
            @RequestParam String runId,
            @RequestParam int index
    ) {
        return ApiResponse.success(
                SuccessStatus.OK,
                new EventCreated(service.create(member.id(), runId, index))
        );
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<PushRetryMeasurementService.RunStatus>> status(
            @RequestParam String runId
    ) {
        return ApiResponse.success(SuccessStatus.OK, service.status(runId));
    }

    @PostMapping("/fail")
    public ResponseEntity<ApiResponse<PushRetryMeasurementService.RunStatus>> fail(
            @RequestParam String runId
    ) {
        return ApiResponse.success(SuccessStatus.OK, service.fail(runId));
    }

    @PostMapping("/replay")
    public ResponseEntity<ApiResponse<PushRetryMeasurementService.RunStatus>> replay(
            @RequestParam String runId
    ) {
        return ApiResponse.success(SuccessStatus.OK, service.replay(runId));
    }

    public record EventCreated(UUID eventId) {
    }
}
