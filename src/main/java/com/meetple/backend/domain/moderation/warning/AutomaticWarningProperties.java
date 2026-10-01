package com.meetple.backend.domain.moderation.warning;

import static com.meetple.backend.domain.moderation.analysis.ReportAnalysisContracts.ModerationReportType;

import java.math.BigDecimal;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("moderation.auto-warning")
public record AutomaticWarningProperties(
        boolean enabled,
        BigDecimal minConfidence,
        Set<ModerationReportType> allowedReportTypes
) {
    private static final BigDecimal DEFAULT_MIN_CONFIDENCE = new BigDecimal("0.95");

    public AutomaticWarningProperties {
        minConfidence = minConfidence == null ? DEFAULT_MIN_CONFIDENCE : minConfidence;
        allowedReportTypes = allowedReportTypes == null
                ? Set.of(ModerationReportType.SPAM)
                : Set.copyOf(allowedReportTypes);
        if (minConfidence.compareTo(BigDecimal.ZERO) < 0
                || minConfidence.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("자동 경고 확신도 기준은 0 이상 1 이하여야 합니다.");
        }
    }
}
