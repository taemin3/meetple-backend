package com.meetple.backend.domain.moderation.analysis;

import static com.meetple.backend.domain.moderation.analysis.ReportAnalysisContracts.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.meetple.backend.domain.moderation.analysis.ReportAnalysisRepository.AnalysisLock;
import com.meetple.backend.domain.moderation.entity.ReportReason;
import com.meetple.backend.domain.moderation.entity.ReportTargetType;
import com.meetple.backend.domain.moderation.policy.ModerationPolicyContracts.Candidate;
import com.meetple.backend.domain.moderation.policy.ModerationPolicyContracts.Candidates;
import com.meetple.backend.domain.moderation.policy.ModerationPolicyService;
import com.meetple.backend.domain.moderation.policy.ModerationPolicyTargetType;
import com.meetple.backend.domain.moderation.policy.ModerationPolicyType;
import com.meetple.backend.global.exception.BadRequestException;
import com.meetple.backend.global.exception.ConflictException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
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
    @Mock ModerationPolicyService policyService;

    private ReportAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new ReportAnalysisService(repository, policyService);
    }

    @Test
    void contextStartsProcessingAndReturnsImmutableEvidence() {
        Context context = new Context(
                10L,
                ReportTargetType.CHAT_MESSAGE,
                ReportReason.SPAM,
                null,
                List.of(new Evidence(20L, ReportTargetType.CHAT_MESSAGE, "광고 메시지"))
        );
        given(repository.findContext(10L)).willReturn(Optional.of(context));

        assertThat(service.getContext(10L)).isEqualTo(context);

        verify(repository).markProcessing(10L);
    }

    @Test
    void auditedSearchRejectsTargetTypeDifferentFromReport() {
        given(repository.findTargetType(10L)).willReturn(Optional.of(ReportTargetType.MEMBER));

        assertThatThrownBy(() -> service.searchPolicies(searchRequest(ReportTargetType.MEETING)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("신고 대상 유형이 일치하지 않습니다.");

        verify(policyService, never()).search(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void auditedSearchStoresExactCandidatesAndReturnsRetrievalId() {
        Candidate candidate = candidate();
        given(repository.findTargetType(10L)).willReturn(Optional.of(ReportTargetType.MEETING));
        given(policyService.search(org.mockito.ArgumentMatchers.any()))
                .willReturn(new Candidates(List.of(candidate), false));
        given(repository.recordRetrieval(
                10L,
                "text-embedding-3-small",
                "폭언",
                ModerationPolicyType.ABUSE_OR_HARASSMENT,
                List.of(candidate)
        )).willReturn(30L);

        AuditedPolicyCandidates result = service.searchPolicies(
                searchRequest(ReportTargetType.MEETING)
        );

        assertThat(result.retrievalId()).isEqualTo(30L);
        assertThat(result.items()).containsExactly(candidate);
    }

    @Test
    void completeStoresOnlyEvidenceAndPoliciesFromTheReportRetrieval() {
        CompleteRequest request = completeRequest(RecommendedAction.SUSPEND_3_DAYS);
        given(repository.lockAnalysis(10L)).willReturn(Optional.of(new AnalysisLock(
                AnalysisStatus.PROCESSING,
                null,
                ReportTargetType.CHAT_MESSAGE
        )));
        given(repository.retrievalBelongsToReport(10L, 30L)).willReturn(true);
        given(repository.findEvidenceIds(10L, List.of(20L))).willReturn(Set.of(20L));
        given(repository.findRetrievedPolicyIds(30L, List.of(40L))).willReturn(Set.of(40L));

        Completion completion = service.complete(10L, request);

        assertThat(completion.idempotent()).isFalse();
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(repository).complete(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.same(request),
                hash.capture(),
                org.mockito.ArgumentMatchers.eq(List.of(20L)),
                org.mockito.ArgumentMatchers.eq(List.of(40L))
        );
        assertThat(hash.getValue()).matches("[0-9a-f]{64}");
    }

    @Test
    void sameCompletedPayloadIsIdempotentButDifferentPayloadConflicts() {
        CompleteRequest request = completeRequest(RecommendedAction.SUSPEND_3_DAYS);
        given(repository.lockAnalysis(10L))
                .willReturn(Optional.of(new AnalysisLock(
                        AnalysisStatus.PROCESSING,
                        null,
                        ReportTargetType.CHAT_MESSAGE
                )));
        given(repository.retrievalBelongsToReport(10L, 30L)).willReturn(true);
        given(repository.findEvidenceIds(10L, List.of(20L))).willReturn(Set.of(20L));
        given(repository.findRetrievedPolicyIds(30L, List.of(40L))).willReturn(Set.of(40L));
        service.complete(10L, request);
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(repository).complete(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.same(request),
                hash.capture(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyList()
        );

        given(repository.lockAnalysis(10L)).willReturn(Optional.of(new AnalysisLock(
                AnalysisStatus.COMPLETED,
                hash.getValue(),
                ReportTargetType.CHAT_MESSAGE
        )));
        assertThat(service.complete(10L, request).idempotent()).isTrue();

        CompleteRequest changed = new CompleteRequest(
                request.policyRetrievalId(),
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
    void completeRejectsPolicyThatWasNotInRecordedRetrieval() {
        CompleteRequest request = completeRequest(RecommendedAction.SUSPEND_3_DAYS);
        given(repository.lockAnalysis(10L)).willReturn(Optional.of(new AnalysisLock(
                AnalysisStatus.PROCESSING,
                null,
                ReportTargetType.CHAT_MESSAGE
        )));
        given(repository.retrievalBelongsToReport(10L, 30L)).willReturn(true);
        given(repository.findEvidenceIds(10L, List.of(20L))).willReturn(Set.of(20L));
        given(repository.findRetrievedPolicyIds(30L, List.of(40L))).willReturn(Set.of());

        assertThatThrownBy(() -> service.complete(10L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("검색 결과에 없는 운영 정책이 포함되어 있습니다.");
    }

    @Test
    void forceDeleteMeetingIsRejectedForMemberReport() {
        CompleteRequest request = completeRequest(RecommendedAction.FORCE_DELETE_MEETING);
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

    private AuditedPolicySearchRequest searchRequest(ReportTargetType targetType) {
        return new AuditedPolicySearchRequest(
                10L,
                "폭언",
                targetType,
                ModerationPolicyType.ABUSE_OR_HARASSMENT,
                Collections.nCopies(1536, 0.01),
                "text-embedding-3-small",
                5
        );
    }

    private CompleteRequest completeRequest(RecommendedAction action) {
        return new CompleteRequest(
                30L,
                ModerationReportType.ABUSE_OR_HARASSMENT,
                RiskLevel.HIGH,
                ModerationPriority.HIGH,
                "폭언 신고",
                "증거와 운영 정책에 따라 폭언으로 판단했습니다.",
                List.of(20L),
                List.of(40L),
                new BigDecimal("0.91"),
                action
        );
    }

    private Candidate candidate() {
        return new Candidate(
                40L,
                41L,
                "ABUSE-001",
                "괴롭힘 금지",
                ModerationPolicyType.ABUSE_OR_HARASSMENT,
                ModerationPolicyTargetType.ALL,
                1,
                "1.1",
                "타인을 모욕하거나 괴롭히면 안 됩니다.",
                "a".repeat(64),
                LocalDate.of(2026, 1, 1),
                null,
                true,
                0.1,
                0.9
        );
    }
}
