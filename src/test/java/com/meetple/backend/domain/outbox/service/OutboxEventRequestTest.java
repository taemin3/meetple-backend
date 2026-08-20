package com.meetple.backend.domain.outbox.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meetple.backend.domain.outbox.event.OutboxEventTopic;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OutboxEventRequestTest {

    @Test
    void rejectsInvalidSchemaVersion() {
        assertThatThrownBy(() -> new OutboxEventRequest(
                "notification",
                "101",
                "PARTICIPATION_APPROVED",
                "member:7",
                OutboxEventTopic.PUSH_NOTIFICATION,
                0,
                "notification:participation-approved:101:7",
                Map.of("recipientMemberId", 7L)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schemaVersion");
    }

    @Test
    void rejectsBlankRoutingKey() {
        assertThatThrownBy(() -> new OutboxEventRequest(
                "notification",
                "101",
                "PARTICIPATION_APPROVED",
                " ",
                OutboxEventTopic.PUSH_NOTIFICATION,
                1,
                "notification:participation-approved:101:7",
                Map.of("recipientMemberId", 7L)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventKey");
    }
}
