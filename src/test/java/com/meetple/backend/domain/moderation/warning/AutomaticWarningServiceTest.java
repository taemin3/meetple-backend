package com.meetple.backend.domain.moderation.warning;

import static com.meetple.backend.domain.moderation.analysis.ReportAnalysisContracts.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.repository.MemberRepository;
import com.meetple.backend.domain.notification.service.NotificationService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AutomaticWarningServiceTest {
    @Mock AutomaticWarningRepository repository;
    @Mock MemberRepository memberRepository;
    @Mock NotificationService notificationService;

    private Member target;

    @BeforeEach
    void setUp() {
        target = Member.createUser("target@example.com", "password", "target", "Seoul");
        ReflectionTestUtils.setField(target, "id", 2L);
    }

    @Test
    void eligibleAnalysisCreatesOneWarningAndFixedNotification() {
        AutomaticWarningService service = service(true, "0.95", Set.of(ModerationReportType.SPAM));
        given(repository.insertIfAbsent(10L)).willReturn(Optional.of(2L));
        given(memberRepository.findById(2L)).willReturn(Optional.of(target));

        assertThat(service.issueIfEligible(10L, request(
                ModerationReportType.SPAM,
                RiskLevel.LOW,
                ModerationPriority.NORMAL,
                new BigDecimal("0.95"),
                RecommendedAction.WARNING
        ))).isTrue();

        verify(notificationService).notify(
                target,
                AutomaticWarningService.NOTIFICATION_TYPE,
                AutomaticWarningService.NOTIFICATION_TITLE,
                AutomaticWarningService.NOTIFICATION_MESSAGE,
                null
        );
    }

    @Test
    void duplicateWarningDoesNotSendAnotherNotification() {
        AutomaticWarningService service = service(true, "0.95", Set.of(ModerationReportType.SPAM));
        given(repository.insertIfAbsent(10L)).willReturn(Optional.empty());

        assertThat(service.issueIfEligible(10L, request(
                ModerationReportType.SPAM,
                RiskLevel.LOW,
                ModerationPriority.LOW,
                new BigDecimal("0.99"),
                RecommendedAction.WARNING
        ))).isFalse();

        verify(notificationService, never()).notify(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void disabledOrUnsafeAnalysisDoesNotCreateWarning() {
        assertThat(service(false, "0.95", Set.of(ModerationReportType.SPAM)).issueIfEligible(
                10L,
                request(
                        ModerationReportType.SPAM,
                        RiskLevel.LOW,
                        ModerationPriority.LOW,
                        new BigDecimal("0.99"),
                        RecommendedAction.WARNING
                )
        )).isFalse();
        AutomaticWarningService enabled = service(true, "0.95", Set.of(ModerationReportType.SPAM));
        assertThat(enabled.issueIfEligible(10L, request(
                ModerationReportType.SPAM,
                RiskLevel.MEDIUM,
                ModerationPriority.NORMAL,
                new BigDecimal("0.99"),
                RecommendedAction.WARNING
        ))).isFalse();
        assertThat(enabled.issueIfEligible(10L, request(
                ModerationReportType.SPAM,
                RiskLevel.LOW,
                ModerationPriority.HIGH,
                new BigDecimal("0.99"),
                RecommendedAction.WARNING
        ))).isFalse();
        assertThat(enabled.issueIfEligible(10L, request(
                ModerationReportType.SPAM,
                RiskLevel.LOW,
                ModerationPriority.LOW,
                new BigDecimal("0.94"),
                RecommendedAction.WARNING
        ))).isFalse();
        assertThat(enabled.issueIfEligible(10L, request(
                ModerationReportType.ABUSE_OR_HARASSMENT,
                RiskLevel.LOW,
                ModerationPriority.LOW,
                new BigDecimal("0.99"),
                RecommendedAction.WARNING
        ))).isFalse();
        assertThat(enabled.issueIfEligible(10L, request(
                ModerationReportType.SPAM,
                RiskLevel.LOW,
                ModerationPriority.LOW,
                new BigDecimal("0.99"),
                RecommendedAction.MANUAL_REVIEW
        ))).isFalse();

        verify(repository, never()).insertIfAbsent(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void confidenceThresholdMustBeBetweenZeroAndOne() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new AutomaticWarningProperties(true, new BigDecimal("1.01"), Set.of())
        ).isInstanceOf(IllegalArgumentException.class);
    }

    private AutomaticWarningService service(
            boolean enabled,
            String minConfidence,
            Set<ModerationReportType> allowedReportTypes
    ) {
        return new AutomaticWarningService(
                new AutomaticWarningProperties(
                        enabled,
                        new BigDecimal(minConfidence),
                        allowedReportTypes
                ),
                repository,
                memberRepository,
                notificationService
        );
    }

    private CompleteRequest request(
            ModerationReportType reportType,
            RiskLevel riskLevel,
            ModerationPriority priority,
            BigDecimal confidence,
            RecommendedAction action
    ) {
        return new CompleteRequest(
                reportType,
                riskLevel,
                priority,
                "신고 요약",
                "증거와 운영 정책에 따른 판단",
                List.of(10L),
                List.of(40L),
                confidence,
                action
        );
    }
}
