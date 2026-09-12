package com.meetple.backend.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class ChatSendMeasurementRecorderTest {

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void recordsOnlyAfterCommitAndKeepsMessageIdentity() {
        ChatSendMeasurementRecorder recorder = new ChatSendMeasurementRecorder(true);
        UUID clientMessageId = UUID.randomUUID();
        TransactionSynchronizationManager.initSynchronization();

        ChatSendMeasurementRecorder.Observation observation = recorder.start(
                clientMessageId,
                "[CHAT-LOAD:focused-r1] payload"
        );
        String value = observation.measure(
                ChatSendMeasurementRecorder.Observation.LOCK_LOOKUP,
                () -> "locked"
        );
        observation.markMessageId(42L);
        observation.markServiceReturned();

        assertThat(value).isEqualTo("locked");
        assertThat(recorder.report("focused-r1").samples()).isZero();

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(
                        TransactionSynchronization.STATUS_COMMITTED
                ));

        ChatSendMeasurementRecorder.RunReport report = recorder.report("focused-r1");
        assertThat(report.samples()).isEqualTo(1);
        assertThat(report.committed()).isEqualTo(1);
        assertThat(report.records()).singleElement().satisfies(sample -> {
            assertThat(sample.clientMessageId()).isEqualTo(clientMessageId);
            assertThat(sample.messageId()).isEqualTo(42L);
            assertThat(sample.transactionStatus()).isEqualTo("COMMITTED");
            assertThat(sample.phaseMicros()).containsKeys("lockLookup", "serviceBody");
        });
        assertThat(report.phaseMicros().get("transactionCommit").count()).isEqualTo(1);
    }

    @Test
    void ignoresOrdinaryChatMessagesWhenMeasurementIsEnabled() {
        ChatSendMeasurementRecorder recorder = new ChatSendMeasurementRecorder(true);

        ChatSendMeasurementRecorder.Observation observation = recorder.start(
                UUID.randomUUID(),
                "ordinary message"
        );
        observation.markServiceReturned();

        assertThat(recorder.report("focused-r1").samples()).isZero();
    }
}
