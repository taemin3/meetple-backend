package com.meetple.backend.global.performance;

import com.meetple.backend.domain.chat.service.ChatSendMeasurementRecorder;
import com.meetple.backend.global.response.ApiResponse;
import com.meetple.backend.global.response.SuccessStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/performance/chat-send")
@ConditionalOnProperty(
        prefix = "meetple.performance.chat-send",
        name = "enabled",
        havingValue = "true"
)
public class ChatSendMeasurementController {

    private final ChatSendMeasurementRecorder recorder;

    @GetMapping("/report")
    public ResponseEntity<ApiResponse<ChatSendMeasurementRecorder.RunReport>> report(
            @RequestParam String runId
    ) {
        return ApiResponse.success(SuccessStatus.OK, recorder.report(runId));
    }

    @PostMapping("/reset")
    public ResponseEntity<ApiResponse<Void>> reset(@RequestParam String runId) {
        recorder.reset(runId);
        return ApiResponse.successOnly(SuccessStatus.OK);
    }
}
