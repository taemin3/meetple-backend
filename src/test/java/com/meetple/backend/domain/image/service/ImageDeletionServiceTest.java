package com.meetple.backend.domain.image.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.meetple.backend.domain.outbox.event.OutboxEventTopic;
import com.meetple.backend.domain.outbox.service.OutboxEventPublisher;
import com.meetple.backend.domain.outbox.service.OutboxEventRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImageDeletionServiceTest {

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    @InjectMocks
    private ImageDeletionService imageDeletionService;

    @Test
    void schedulePublishesOnlyDistinctNonBlankObjectKeysToOutbox() {
        imageDeletionService.schedule(List.of(" images/profile/1/old.png ", "", "images/profile/1/old.png"));

        ArgumentCaptor<OutboxEventRequest> captor = ArgumentCaptor.forClass(OutboxEventRequest.class);
        verify(outboxEventPublisher, times(1)).publish(captor.capture());
        OutboxEventRequest request = captor.getValue();
        assertThat(request.aggregateType()).isEqualTo("image");
        assertThat(request.aggregateId()).isEqualTo("images/profile/1/old.png");
        assertThat(request.eventType()).isEqualTo("IMAGE_DELETE_REQUESTED");
        assertThat(request.eventKey()).isEqualTo("images/profile/1/old.png");
        assertThat(request.topic()).isEqualTo(OutboxEventTopic.IMAGE_DELETION);
        assertThat(request.schemaVersion()).isEqualTo(1);
        assertThat(request.deduplicationKey()).startsWith("image-delete:");
        assertThat(request.data()).isEqualTo(Map.of("objectKey", "images/profile/1/old.png"));
    }
}
