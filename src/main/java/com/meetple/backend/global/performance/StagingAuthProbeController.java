package com.meetple.backend.global.performance;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/performance/auth-probe")
@ConditionalOnProperty(
        prefix = "meetple.performance.auth-probe",
        name = "enabled",
        havingValue = "true"
)
public class StagingAuthProbeController {

    @GetMapping
    public ResponseEntity<Void> probe() {
        return ResponseEntity.noContent().build();
    }
}
