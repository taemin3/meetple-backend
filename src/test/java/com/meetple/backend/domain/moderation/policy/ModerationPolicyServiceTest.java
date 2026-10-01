package com.meetple.backend.domain.moderation.policy;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import com.meetple.backend.global.exception.BadRequestException;
import java.util.ArrayList;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModerationPolicyServiceTest {
    @Mock ModerationPolicyRepository repository;

    @Test
    void rejectsZeroVectorBeforeQueryingDatabase() {
        var service = new ModerationPolicyService(repository);
        var values = new ArrayList<>(Collections.nCopies(1536, 0.0));

        assertThatThrownBy(() -> ModerationPolicyService.validateEmbedding(values, "model"))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsNonFiniteOrFloatOverflowVector() {
        var values = new ArrayList<>(Collections.nCopies(1536, 0.0));
        values.set(0, Double.MAX_VALUE);

        assertThatThrownBy(() -> ModerationPolicyService.validateEmbedding(values, "model"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsUntrimmedEmbeddingModel() {
        var values = new ArrayList<>(Collections.nCopies(1536, 0.0));
        values.set(0, 1.0);

        assertThatThrownBy(() -> ModerationPolicyService.validateEmbedding(values, " model "))
                .isInstanceOf(BadRequestException.class);
    }
}
