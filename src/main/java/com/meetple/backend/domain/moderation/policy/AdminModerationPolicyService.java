package com.meetple.backend.domain.moderation.policy;

import static com.meetple.backend.domain.moderation.policy.AdminModerationPolicyContracts.*;

import com.meetple.backend.domain.moderation.policy.AdminModerationPolicyRepository.ClauseRow;
import com.meetple.backend.domain.moderation.policy.AdminModerationPolicyRepository.PolicyRow;
import com.meetple.backend.global.exception.BadRequestException;
import com.meetple.backend.global.exception.ConflictException;
import com.meetple.backend.global.exception.NotFoundException;
import com.meetple.backend.global.response.PageResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminModerationPolicyService {
    static final String EMBEDDING_MODEL = "text-embedding-3-small";
    private static final int MAX_PAGE_SIZE = 100;
    private static final String POLICY_NOT_FOUND_MESSAGE = "운영 정책을 찾을 수 없습니다.";

    private final AdminModerationPolicyRepository repository;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public AdminModerationPolicyService(AdminModerationPolicyRepository repository) {
        this(repository, Clock.system(ZoneId.of("Asia/Seoul")));
    }

    AdminModerationPolicyService(AdminModerationPolicyRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public PageResponse<PolicySummary> getPolicies(
            String policyCode,
            Boolean active,
            int page,
            int size
    ) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("페이지 번호와 크기가 올바르지 않습니다.");
        }
        return PageResponse.from(repository.findAll(
                policyCode,
                active,
                page,
                size,
                EMBEDDING_MODEL
        ));
    }

    public PolicyDetail getPolicy(long policyId) {
        PolicyRow policy = repository.findById(policyId)
                .orElseThrow(() -> new NotFoundException(POLICY_NOT_FOUND_MESSAGE));
        return detail(policy);
    }

    @Transactional
    public PolicyDetail createPolicy(long administratorMemberId, CreatePolicyRequest request) {
        validateEffectiveRange(request.effectiveFrom(), request.effectiveTo());
        List<ClauseRow> clauses = clauses(request.clauses());
        String policyCode = request.policyCode().strip();
        if (repository.existsByPolicyCode(policyCode)) {
            throw new ConflictException("이미 등록된 운영 정책 코드입니다.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        long policyId;
        try {
            policyId = repository.insertPolicy(
                    policyCode,
                    request.title().strip(),
                    request.policyType(),
                    request.targetType(),
                    request.effectiveFrom(),
                    request.effectiveTo(),
                    1,
                    now
            );
            repository.insertClauses(policyId, clauses, now);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("운영 정책 코드 또는 조항 코드가 중복되었습니다.");
        }
        repository.insertAudit(policyId, administratorMemberId, PolicyAuditAction.CREATED, now);
        return getPolicy(policyId);
    }

    @Transactional
    public PolicyDetail createVersion(
            long administratorMemberId,
            long sourcePolicyId,
            CreateVersionRequest request
    ) {
        validateEffectiveRange(request.effectiveFrom(), request.effectiveTo());
        List<ClauseRow> clauses = clauses(request.clauses());
        PolicyRow sourceSnapshot = repository.findById(sourcePolicyId)
                .orElseThrow(() -> new NotFoundException(POLICY_NOT_FOUND_MESSAGE));
        int latestVersion = repository.lockVersionsAndFindLatest(sourceSnapshot.policyCode());
        PolicyRow source = repository.findByIdForUpdate(sourcePolicyId)
                .orElseThrow(() -> new NotFoundException(POLICY_NOT_FOUND_MESSAGE));
        if (source.version() != latestVersion) {
            throw new ConflictException("최신 버전의 운영 정책에서만 새 버전을 만들 수 있습니다.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        long policyId;
        try {
            policyId = repository.insertPolicy(
                    source.policyCode(),
                    request.title().strip(),
                    request.policyType(),
                    request.targetType(),
                    request.effectiveFrom(),
                    request.effectiveTo(),
                    latestVersion + 1,
                    now
            );
            repository.insertClauses(policyId, clauses, now);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("운영 정책 버전 또는 조항 코드가 중복되었습니다.");
        }
        repository.insertAudit(
                policyId,
                administratorMemberId,
                PolicyAuditAction.VERSION_CREATED,
                now
        );
        return getPolicy(policyId);
    }

    @Transactional
    public PolicyDetail updateActivation(
            long administratorMemberId,
            long policyId,
            ActivationRequest request
    ) {
        PolicyRow policySnapshot = repository.findById(policyId)
                .orElseThrow(() -> new NotFoundException(POLICY_NOT_FOUND_MESSAGE));
        repository.lockVersionsAndFindLatest(policySnapshot.policyCode());
        PolicyRow policy = repository.findByIdForUpdate(policyId)
                .orElseThrow(() -> new NotFoundException(POLICY_NOT_FOUND_MESSAGE));
        boolean active = request.active();
        if (policy.active() == active) {
            return detail(policy);
        }
        if (active) {
            int missingEmbeddingCount = repository.countMissingEmbeddings(
                    policyId,
                    EMBEDDING_MODEL
            );
            if (missingEmbeddingCount > 0) {
                throw new ConflictException(
                        "모든 정책 조항의 임베딩을 동기화한 뒤 활성화할 수 있습니다."
                );
            }
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (active) {
            List<Long> deactivatedPolicyIds = repository.deactivateOtherVersions(
                    policyId,
                    policy.policyCode(),
                    now
            );
            deactivatedPolicyIds.forEach(deactivatedPolicyId -> repository.insertAudit(
                    deactivatedPolicyId,
                    administratorMemberId,
                    PolicyAuditAction.DEACTIVATED,
                    now
            ));
        }
        repository.updateActivation(policyId, active, now);
        repository.insertAudit(
                policyId,
                administratorMemberId,
                active ? PolicyAuditAction.ACTIVATED : PolicyAuditAction.DEACTIVATED,
                now
        );
        return getPolicy(policyId);
    }

    private PolicyDetail detail(PolicyRow policy) {
        List<PolicyClause> clauses = repository.findClauses(policy.policyId(), EMBEDDING_MODEL);
        int missingEmbeddingCount = (int) clauses.stream()
                .filter(clause -> !clause.embedded())
                .count();
        return new PolicyDetail(
                policy.policyId(),
                policy.policyCode(),
                policy.title(),
                policy.policyType(),
                policy.targetType(),
                policy.effectiveFrom(),
                policy.effectiveTo(),
                policy.active(),
                policy.version(),
                EMBEDDING_MODEL,
                missingEmbeddingCount,
                clauses,
                repository.findAudits(policy.policyId()),
                policy.createdAt(),
                policy.updatedAt()
        );
    }

    private static List<ClauseRow> clauses(List<ClauseRequest> requests) {
        Set<String> clauseCodes = new HashSet<>();
        return java.util.stream.IntStream.range(0, requests.size())
                .mapToObj(index -> {
                    ClauseRequest request = requests.get(index);
                    String clauseCode = request.clauseCode().strip();
                    if (!clauseCodes.add(clauseCode)) {
                        throw new BadRequestException("같은 정책 안에서 조항 코드는 중복될 수 없습니다.");
                    }
                    String content = request.content().strip();
                    return new ClauseRow(clauseCode, index, content, sha256(content));
                })
                .toList();
    }

    private static void validateEffectiveRange(
            java.time.LocalDate effectiveFrom,
            java.time.LocalDate effectiveTo
    ) {
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new BadRequestException("정책 종료일은 시작일보다 빠를 수 없습니다.");
        }
    }

    static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", ex);
        }
    }
}
