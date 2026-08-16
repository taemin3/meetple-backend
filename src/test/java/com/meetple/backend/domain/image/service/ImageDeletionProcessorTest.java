package com.meetple.backend.domain.image.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.meetple.backend.domain.image.entity.ImageDeletionTask;
import com.meetple.backend.domain.image.repository.ImageDeletionTaskRepository;
import com.meetple.backend.domain.image.storage.ImageStorageClient;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImageDeletionProcessorTest {

    @Mock
    private ImageDeletionTaskRepository taskRepository;

    @Mock
    private ImageStorageClient imageStorageClient;

    @InjectMocks
    private ImageDeletionProcessor processor;

    @Test
    void deleteScheduledImagesRemovesSuccessfulTask() {
        ImageDeletionTask task = ImageDeletionTask.create("images/profile/1/old.png", LocalDateTime.now());
        given(taskRepository.findReadyForUpdate(any(), any())).willReturn(List.of(task));

        processor.deleteScheduledImages();

        verify(imageStorageClient).deleteObject("images/profile/1/old.png");
        verify(taskRepository).delete(task);
    }

    @Test
    void deleteScheduledImagesSchedulesRetryAfterFailure() {
        ImageDeletionTask task = ImageDeletionTask.create("images/profile/1/old.png", LocalDateTime.now());
        given(taskRepository.findReadyForUpdate(any(), any())).willReturn(List.of(task));
        org.mockito.Mockito.doThrow(new IllegalStateException("S3 unavailable"))
                .when(imageStorageClient).deleteObject("images/profile/1/old.png");

        processor.deleteScheduledImages();

        assertThat(task.getAttempts()).isEqualTo(1);
        assertThat(task.getLastError()).isEqualTo("S3 unavailable");
        assertThat(task.getNextAttemptAt()).isAfter(LocalDateTime.now());
        verify(taskRepository, never()).delete(task);
    }
}
