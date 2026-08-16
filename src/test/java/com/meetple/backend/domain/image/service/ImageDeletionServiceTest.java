package com.meetple.backend.domain.image.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.meetple.backend.domain.image.entity.ImageDeletionTask;
import com.meetple.backend.domain.image.repository.ImageDeletionTaskRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImageDeletionServiceTest {

    @Mock
    private ImageDeletionTaskRepository taskRepository;

    @InjectMocks
    private ImageDeletionService imageDeletionService;

    @Test
    void scheduleStoresOnlyDistinctNonBlankObjectKeys() {
        imageDeletionService.schedule(List.of(" images/profile/1/old.png ", "", "images/profile/1/old.png"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ImageDeletionTask>> captor = ArgumentCaptor.forClass(List.class);
        verify(taskRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(ImageDeletionTask::getObjectKey)
                .containsExactly("images/profile/1/old.png");
    }
}
