package com.meetple.backend.domain.push.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class PushEventRecipientDecisionServiceTest {

    @Mock
    private PushEventRecipientDecisionRepository decisionRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private PushEventRecipientDecisionService service;

    @BeforeEach
    void setUpTransactionTemplate() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void snapshotsCurrentSettingForEveryEventRecipient() {
        UUID eventId = UUID.randomUUID();
        given(decisionRepository.findAllByEventIdAndMemberIdIn(
                eq(eventId),
                argThat(ids -> ids.size() == 2 && ids.containsAll(List.of(2L, 3L)))
        )).willReturn(List.of());

        List<Long> enabledRecipients = service.resolveEnabledRecipients(
                eventId,
                List.of(2L, 3L),
                List.of(3L)
        );

        ArgumentCaptor<List<PushEventRecipientDecision>> captor = ArgumentCaptor.forClass(List.class);
        verify(decisionRepository).saveAllAndFlush(captor.capture());
        assertThat(captor.getValue())
                .extracting(
                        PushEventRecipientDecision::getMemberId,
                        PushEventRecipientDecision::isSuppressed
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(2L, true),
                        org.assertj.core.groups.Tuple.tuple(3L, false)
                );
        assertThat(enabledRecipients).containsExactly(3L);
    }

    @Test
    void keepsInitialSuppressionWhenMemberReenablesBeforeRetry() {
        UUID eventId = UUID.randomUUID();
        PushEventRecipientDecision suppressed = PushEventRecipientDecision.create(
                eventId,
                2L,
                true
        );
        PushEventRecipientDecision enabled = PushEventRecipientDecision.create(
                eventId,
                3L,
                false
        );
        given(decisionRepository.findAllByEventIdAndMemberIdIn(
                eq(eventId),
                argThat(ids -> ids.size() == 2 && ids.containsAll(List.of(2L, 3L)))
        )).willReturn(List.of(suppressed, enabled));

        List<Long> enabledRecipients = service.resolveEnabledRecipients(
                eventId,
                List.of(2L, 3L),
                List.of(2L, 3L)
        );

        assertThat(enabledRecipients).containsExactly(3L);
        verify(decisionRepository, times(0)).saveAllAndFlush(any());
    }

    @Test
    void retriesWhenConcurrentDecisionInsertWinsUniqueConstraint() {
        UUID eventId = UUID.randomUUID();
        PushEventRecipientDecision persisted = PushEventRecipientDecision.create(
                eventId,
                2L,
                true
        );
        given(decisionRepository.findAllByEventIdAndMemberIdIn(
                eq(eventId),
                argThat(ids -> ids.size() == 1 && ids.contains(2L))
        ))
                .willReturn(List.of())
                .willReturn(List.of(persisted));
        given(decisionRepository.saveAllAndFlush(any()))
                .willThrow(new DataIntegrityViolationException("concurrent decision"));

        List<Long> enabledRecipients = service.resolveEnabledRecipients(
                eventId,
                List.of(2L),
                List.of()
        );

        assertThat(enabledRecipients).isEmpty();
        verify(transactionTemplate, times(2)).execute(any());
    }
}
