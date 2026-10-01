package com.meetple.backend.domain.moderation.policy;

import static com.meetple.backend.domain.moderation.policy.ModerationPolicyContracts.*;

import com.meetple.backend.global.exception.BadRequestException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ModerationPolicyService {
    private static final int MAX_EMBEDDING_JOBS = 100;
    private final ModerationPolicyRepository repository;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public ModerationPolicyService(ModerationPolicyRepository repository) {
        this(repository, Clock.system(ZoneId.of("Asia/Seoul")));
    }

    ModerationPolicyService(ModerationPolicyRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Candidates search(SearchRequest request) {
        validateEmbedding(request.queryEmbedding(), request.queryEmbeddingModel());
        return repository.search(
                request.keyword(),
                request.targetType(),
                request.policyType(),
                request.queryEmbedding(),
                request.queryEmbeddingModel(),
                request.limit(),
                LocalDate.now(clock)
        );
    }

    public EmbeddingJobs findEmbeddingJobs(String embeddingModel, int limit) {
        validateEmbeddingModel(embeddingModel);
        if (limit < 1 || limit > MAX_EMBEDDING_JOBS) {
            throw new BadRequestException("임베딩 작업 수는 1 이상 100 이하여야 합니다.");
        }
        return repository.findEmbeddingJobs(embeddingModel, limit, LocalDate.now(clock));
    }

    @Transactional
    public void upsertEmbedding(long policyChunkId, EmbeddingUpsertRequest request) {
        if (policyChunkId <= 0) {
            throw new BadRequestException("운영 정책 조항 ID가 올바르지 않습니다.");
        }
        validateEmbedding(request.embedding(), request.embeddingModel());
        repository.upsertEmbedding(
                policyChunkId,
                request.embeddingModel(),
                request.contentHash(),
                request.embedding()
        );
    }

    static void validateEmbedding(List<Double> embedding, String embeddingModel) {
        validateEmbeddingModel(embeddingModel);
        if (embedding == null || embedding.size() != EMBEDDING_DIMENSIONS) {
            throw new BadRequestException("운영 정책 임베딩 차원이 올바르지 않습니다.");
        }
        double norm = 0;
        for (Double value : embedding) {
            if (value == null || !Double.isFinite(value) || !Float.isFinite(value.floatValue())) {
                throw new BadRequestException("운영 정책 임베딩 값이 올바르지 않습니다.");
            }
            norm = Math.hypot(norm, value.floatValue());
        }
        if (norm == 0 || !Double.isFinite(norm)) {
            throw new BadRequestException("운영 정책 임베딩 값이 올바르지 않습니다.");
        }
    }

    private static void validateEmbeddingModel(String embeddingModel) {
        if (embeddingModel == null || embeddingModel.isBlank()
                || embeddingModel.length() > 100
                || !embeddingModel.equals(embeddingModel.strip())) {
            throw new BadRequestException("운영 정책 임베딩 모델이 올바르지 않습니다.");
        }
    }
}
