package com.meetple.backend.global.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatRealtimeMeasurementRecorderTest {

    @Test
    void recordsRunScopedInboundAndAmplifiedOutboundPhases() {
        ChatRealtimeMeasurementRecorder recorder =
                new ChatRealtimeMeasurementRecorder(true);
        UUID clientMessageId = UUID.randomUUID();

        recorder.recordSample(
                clientMessageId,
                "[CHAT-LOAD:focused-r1] payload",
                ChatRealtimeMeasurementRecorder.INBOUND_AUTH,
                10L
        );
        recorder.recordSample(
                clientMessageId,
                null,
                ChatRealtimeMeasurementRecorder.OUTBOUND_AUTH,
                20L
        );
        recorder.recordSample(
                clientMessageId,
                null,
                ChatRealtimeMeasurementRecorder.OUTBOUND_AUTH,
                30L
        );

        ChatRealtimeMeasurementRecorder.RunReport report =
                recorder.report("focused-r1");

        assertThat(report.phaseMicros().get("inboundAuth").count()).isEqualTo(1);
        assertThat(report.phaseMicros().get("outboundAuth").count()).isEqualTo(2);
        assertThat(report.phaseMicros().get("outboundAuth").p50()).isEqualTo(20L);
        assertThat(report.phaseMicros().get("outboundAuth").p95()).isEqualTo(30L);
    }

    @Test
    void resetRemovesRealtimeSamplesAndMessageTrace() {
        ChatRealtimeMeasurementRecorder recorder =
                new ChatRealtimeMeasurementRecorder(true);
        UUID clientMessageId = UUID.randomUUID();
        recorder.recordSample(
                clientMessageId,
                "[CHAT-LOAD:focused-r1] payload",
                ChatRealtimeMeasurementRecorder.INBOUND_AUTH,
                10L
        );

        recorder.reset("focused-r1");
        recorder.recordSample(
                clientMessageId,
                null,
                ChatRealtimeMeasurementRecorder.OUTBOUND_AUTH,
                20L
        );

        ChatRealtimeMeasurementRecorder.RunReport report =
                recorder.report("focused-r1");
        assertThat(report.phaseMicros().values())
                .allSatisfy(percentiles -> assertThat(percentiles.count()).isZero());
    }
}
