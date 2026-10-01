package com.meetple.backend.domain.moderation.analysis;

import static com.meetple.backend.domain.moderation.analysis.ReportAnalysisContracts.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.meetple.backend.domain.moderation.analysis.ReportAnalysisRepository.AnalysisLock;
import com.meetple.backend.domain.moderation.entity.ReportReason;
import com.meetple.backend.domain.moderation.entity.ReportTargetType;
import com.meetple.backend.global.exception.BadRequestException;
import com.meetple.backend.global.exception.ConflictException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportAnalysisServiceTest {
    @Mock ReportAnalysisRepository repository;

    private ReportAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new ReportAnalysisService(repository);
    }

    @Test
    void contextStartsProcessingAndReturnsReportSnapshot() {
        Context context = new Context(
                10L,
                ReportTargetType.CHAT_MESSAGE,
                ReportReason.SPAM,
                null,
                List.of(new Evidence(10L, ReportTargetType.CHAT_MESSAGE, "광고 메시지"))
        );
        given(repository.findContext(10L)).willReturn(Optional.of(context));

        assertThat(service.getContext(10L)).isEqualTo(context);

        verify(repository).markProcessing(10L);
    }

    @Test
    void completeStoresOnlyApplicablePolicies() {
        CompleteRequest request = completeRequest(
                List.of(10L),
                List.of(40L),
                RecommendedAction.SUSPEND_3_DAYS
        );
        given(repository.lockAnalysis(10L)).willReturn(Optional.of(new AnalysisLock(
                AnalysisStatus.PROCESSING,
                null,
                ReportTargetType.CHAT_MESSAGE
        )));
        given(repository.findApplicablePolicyIds(
                ReportTargetType.CHAT_MESSAGE,
                List.of(40L)
        )).willReturn(Set.of(40L));

        Completion completion = service.complete(10L, request);

        assertThat(completion.idempotent()).isFalse();
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(repository).complete(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.same(request),
                hash.capture(),
                org.mockito.ArgumentMatchers.eq(List.of(40L))
        );
        assertThat(hash.getValue()).matches("[0-9a-f]{64}");
    }

    @Test
    void completeRejectsEvidenceOtherThanReportSnapshot() {
        CompleteRequest request = completeRequest(
                List.of(999L),
                List.of(40L),
                RecommendedAction.SUSPEND_3_DAYS
        );
        given(repository.lockAnalysis(10L)).willReturn(Optional.of(new AnalysisLock(
                AnalysisStatus.PROCESSING,
                null,
                ReportTargetType.CHAT_MESSAGE
        )));

        assertThatThrownBy(() -> service.complete(10L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("신고 스냅샷과 일치하지 않는 증거가 포함되어 있습니다.");
    }

    @Test
    void completeRejectsPolicyThatDoesNotApplyToTarget() {
        CompleteRequest request = completeRequest(
                List.of(10L),
                List.of(40L),
                RecommendedAction.SUSPEND_3_DAYS
        );
        given(repository.lockAnalysis(10L)).willReturn(Optional.of(new AnalysisLock(
                AnalysisStatus.PROCESSING,
                null,
                ReportTargetType.CHAT_MESSAGE
        )));
        given(repository.findApplicablePolicyIds(
                ReportTargetType.CHAT_MESSAGE,
                List.of(40L)
        )).willReturn(Set.of());

        assertThatThrownBy(() -> service.complete(10L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("신고 대상에 적용할 수 없는 운영 정책이 포함되어 있습니다.");
    }

    @Test
    void sameCompletedPayloadIsIdempotentButDifferentPayloadConflicts() {
        CompleteRequest request = completeRequest(
                List.of(10L),
                List.of(40L),
                RecommendedAction.SUSPEND_3_DAYS
        );
        given(repository.lockAnalysis(10L)).willReturn(Optional.of(new AnalysisLock(
                AnalysisStatus.PROCESSING,
                null,
                ReportTargetType.CHAT_MESSAGE
        )));
        given(repository.findApplicablePolicyIds(
                ReportTargetType.CHAT_MESSAGE,
                List.of(40L)
        )).willReturn(Set.of(40L));
        service.complete(10L, request);
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(repository).complete(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.same(request),
                hash.capture(),
                org.mockito.ArgumentMatchers.anyList()
        );

        given(repository.lockAnalysis(10L)).willReturn(Optional.of(new AnalysisLock(
                AnalysisStatus.COMPLETED,
                hash.getValue(),
                ReportTargetType.CHAT_MESSAGE
        )));
        assertThat(service.complete(10L, request).idempotent()).isTrue();

        CompleteRequest changed = new CompleteRequest(
                request.reportType(),
                request.riskLevel(),
                request.priority(),
                "다른 요약",
                request.rationale(),
                request.evidenceIds(),
                request.policyIds(),
                request.confidence(),
                request.recommendedAction()
        );
        assertThatThrownBy(() -> service.complete(10L, changed))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void forceDeleteMeetingIsRejectedForMemberReport() {
        CompleteRequest request = completeRequest(
                List.of(10L),
                List.of(40L),
                RecommendedAction.FORCE_DELETE_MEETING
        );
        given(repository.lockAnalysis(10L)).willReturn(Optional.of(new AnalysisLock(
                AnalysisStatus.PROCESSING,
                null,
                ReportTargetType.MEMBER
        )));

        assertThatThrownBy(() -> service.complete(10L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("모임 강제 삭제는 모임 신고에만 사용할 수 있습니다.");
    }

    @Test
    void completedAnalysisCannotBeMarkedFailed() {
        given(repository.lockAnalysis(10L)).willReturn(Optional.of(new AnalysisLock(
                AnalysisStatus.COMPLETED,
                "a".repeat(64),
                ReportTargetType.MEMBER
        )));

        assertThatThrownBy(() -> service.fail(
                10L,
                new FailureRequest(true, "MODEL_TIMEOUT")
        )).isInstanceOf(ConflictException.class);
    }

    @Test
    void permanentFailureCannotReturnToRetryableState() {
        given(repository.lockAnalysis(10L)).willReturn(Optional.of(new AnalysisLock(
                AnalysisStatus.FAILED_PERMANENT,
                null,
                ReportTargetType.MEMBER
        )));

        assertThatThrownBy(() -> service.fail(
                10L,
                new FailureRequest(true, "MODEL_TIMEOUT")
        )).isInstanceOf(ConflictException.class);
    }

    private CompleteRequest completeRequest(
            List<Long> evidenceIds,
            List<Long> policyIds,
            RecommendedAction action
    ) {
        return new CompleteRequest(
                ModerationReportType.ABUSE_OR_HARASSMENT,
                RiskLevel.HIGH,
                ModerationPriority.HIGH,
                "폭언 신고",
                "증거와 운영 정책에 따라 폭언으로 판단했습니다.",
                evidenceIds,
                policyIds,
                new BigDecimal("0.91"),
                action
        );
    }
}
