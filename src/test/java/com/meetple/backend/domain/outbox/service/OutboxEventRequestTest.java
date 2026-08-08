package com.meetple.backend.domain.outbox.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meetple.backend.domain.push.event.PushEventTopic;
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
                PushEventTopic.NOTIFICATION,
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
                PushEventTopic.NOTIFICATION,
                1,
                "notification:participation-approved:101:7",
                Map.of("recipientMemberId", 7L)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventKey");
    }
}
