package com.meetple.backend.domain.moderation.analysis;

import static com.meetple.backend.domain.moderation.analysis.ReportAnalysisContracts.*;

import com.meetple.backend.domain.moderation.analysis.ReportAnalysisRepository.AnalysisLock;
import com.meetple.backend.domain.moderation.entity.ReportTargetType;
import com.meetple.backend.domain.moderation.policy.ModerationPolicyContracts.Candidates;
import com.meetple.backend.domain.moderation.policy.ModerationPolicyContracts.SearchRequest;
import com.meetple.backend.domain.moderation.policy.ModerationPolicyService;
import com.meetple.backend.global.exception.BadRequestException;
import com.meetple.backend.global.exception.ConflictException;
import com.meetple.backend.global.exception.NotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReportAnalysisService {
    private static final Map<RiskLevel, Set<RecommendedAction>> ALLOWED_ACTIONS = allowedActions();
    private final ReportAnalysisRepository repository;
    private final ModerationPolicyService policyService;

    public ReportAnalysisService(
            ReportAnalysisRepository repository,
            ModerationPolicyService policyService
    ) {
        this.repository = repository;
        this.policyService = policyService;
    }

    @Transactional
    public void initialize(
            long reportId,
            ReportTargetType evidenceType,
            long sourceId,
            String evidenceContent
    ) {
        repository.initialize(
                reportId,
                evidenceType,
                sourceId,
                evidenceContent,
                sha256(List.of(evidenceContent))
        );
    }

    @Transactional
    public Context getContext(long reportId) {
        repository.markProcessing(reportId);
        return repository.findContext(reportId)
                .filter(context -> !context.evidence().isEmpty())
                .orElseThrow(() -> new NotFoundException("신고 분석 문맥을 찾을 수 없습니다."));
    }

    @Transactional
    public AuditedPolicyCandidates searchPolicies(AuditedPolicySearchRequest request) {
        ReportTargetType actualTargetType = repository.findTargetType(request.reportId())
                .orElseThrow(() -> new NotFoundException("신고를 찾을 수 없습니다."));
        if (actualTargetType != request.targetType()) {
            throw new BadRequestException("신고 대상 유형이 일치하지 않습니다.");
        }
        Candidates candidates = policyService.search(new SearchRequest(
                request.keyword(),
                request.targetType(),
                request.policyType(),
                request.queryEmbedding(),
                request.queryEmbeddingModel(),
                request.limit()
        ));
        long retrievalId = repository.recordRetrieval(
                request.reportId(),
                request.queryEmbeddingModel(),
                request.keyword().strip(),
                request.policyType(),
                candidates.items()
        );
        return new AuditedPolicyCandidates(retrievalId, candidates.items(), candidates.hasMore());
    }

    @Transactional
    public Completion complete(long reportId, CompleteRequest request) {
        List<Long> evidenceIds = distinctSorted(request.evidenceIds(), "증거 ID");
        List<Long> policyIds = distinctSorted(request.policyIds(), "운영 정책 ID");
        String resultHash = resultHash(request, evidenceIds, policyIds);
        AnalysisLock analysis = repository.lockAnalysis(reportId)
                .orElseThrow(() -> new NotFoundException("신고 분석을 찾을 수 없습니다."));
        if (analysis.status() == AnalysisStatus.COMPLETED) {
            if (resultHash.equals(analysis.resultHash())) {
                return new Completion(reportId, AnalysisStatus.COMPLETED, true);
            }
            throw new ConflictException("이미 다른 신고 분석 결과가 저장되었습니다.");
        }
        if (analysis.status() == AnalysisStatus.FAILED_PERMANENT) {
            throw new ConflictException("영구 실패한 신고 분석은 완료 처리할 수 없습니다.");
        }
        validateAction(analysis.targetType(), request.riskLevel(), request.recommendedAction());
        if (!repository.retrievalBelongsToReport(reportId, request.policyRetrievalId())) {
            throw new BadRequestException("신고에 속한 운영 정책 검색 이력이 아닙니다.");
        }
        if (!repository.findEvidenceIds(reportId, evidenceIds).equals(Set.copyOf(evidenceIds))) {
            throw new BadRequestException("신고에 속하지 않은 증거가 포함되어 있습니다.");
        }
        if (!repository.findRetrievedPolicyIds(request.policyRetrievalId(), policyIds)
                .equals(Set.copyOf(policyIds))) {
            throw new BadRequestException("검색 결과에 없는 운영 정책이 포함되어 있습니다.");
        }
        repository.complete(reportId, request, resultHash, evidenceIds, policyIds);
        return new Completion(reportId, AnalysisStatus.COMPLETED, false);
    }

    @Transactional
    public void fail(long reportId, FailureRequest request) {
        AnalysisLock analysis = repository.lockAnalysis(reportId)
                .orElseThrow(() -> new NotFoundException("신고 분석을 찾을 수 없습니다."));
        if (analysis.status() == AnalysisStatus.COMPLETED) {
            throw new ConflictException("완료된 신고 분석은 실패 처리할 수 없습니다.");
        }
        if (analysis.status() == AnalysisStatus.FAILED_PERMANENT && request.retryable()) {
            throw new ConflictException("영구 실패한 신고 분석은 재시도 상태로 되돌릴 수 없습니다.");
        }
        repository.fail(reportId, request.retryable(), request.failureCode());
    }

    private void validateAction(
            ReportTargetType targetType,
            RiskLevel riskLevel,
            RecommendedAction recommendedAction
    ) {
        if (!ALLOWED_ACTIONS.get(riskLevel).contains(recommendedAction)) {
            throw new BadRequestException("위험도와 권고 조치가 일치하지 않습니다.");
        }
        if (recommendedAction == RecommendedAction.FORCE_DELETE_MEETING
                && targetType != ReportTargetType.MEETING) {
            throw new BadRequestException("모임 강제 삭제는 모임 신고에만 사용할 수 있습니다.");
        }
    }

    private List<Long> distinctSorted(List<Long> ids, String fieldName) {
        List<Long> sorted = ids.stream().sorted().toList();
        if (Set.copyOf(sorted).size() != sorted.size()) {
            throw new BadRequestException(fieldName + "는 중복될 수 없습니다.");
        }
        return sorted;
    }

    private String resultHash(
            CompleteRequest request,
            List<Long> evidenceIds,
            List<Long> policyIds
    ) {
        return sha256(List.of(
                request.policyRetrievalId().toString(),
                request.reportType().name(),
                request.riskLevel().name(),
                request.priority().name(),
                request.summary().strip(),
                request.rationale().strip(),
                request.confidence().stripTrailingZeros().toPlainString(),
                request.recommendedAction().name(),
                evidenceIds.toString(),
                policyIds.toString()
        ));
    }

    private static String sha256(List<String> values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            values.forEach(value -> {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) ':');
                digest.update(bytes);
            });
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private static Map<RiskLevel, Set<RecommendedAction>> allowedActions() {
        Map<RiskLevel, Set<RecommendedAction>> actions = new EnumMap<>(RiskLevel.class);
        actions.put(RiskLevel.LOW, Set.of(
                RecommendedAction.DISMISS,
                RecommendedAction.WARNING,
                RecommendedAction.MANUAL_REVIEW
        ));
        actions.put(RiskLevel.MEDIUM, Set.of(
                RecommendedAction.WARNING,
                RecommendedAction.SUSPEND_1_DAY,
                RecommendedAction.SUSPEND_3_DAYS,
                RecommendedAction.MANUAL_REVIEW
        ));
        actions.put(RiskLevel.HIGH, Set.of(
                RecommendedAction.SUSPEND_3_DAYS,
                RecommendedAction.SUSPEND_7_DAYS,
                RecommendedAction.PERMANENT_SUSPENSION,
                RecommendedAction.FORCE_DELETE_MEETING,
                RecommendedAction.MANUAL_REVIEW
        ));
        actions.put(RiskLevel.CRITICAL, Set.of(
                RecommendedAction.SUSPEND_7_DAYS,
                RecommendedAction.PERMANENT_SUSPENSION,
                RecommendedAction.FORCE_DELETE_MEETING,
                RecommendedAction.MANUAL_REVIEW
        ));
        return Map.copyOf(actions);
    }
}
