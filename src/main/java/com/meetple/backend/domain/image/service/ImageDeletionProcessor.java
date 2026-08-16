package com.meetple.backend.domain.image.service;

import com.meetple.backend.domain.image.entity.ImageDeletionTask;
import com.meetple.backend.domain.image.repository.ImageDeletionTaskRepository;
import com.meetple.backend.domain.image.storage.ImageStorageClient;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageDeletionProcessor {

    private static final int BATCH_SIZE = 20;

    private final ImageDeletionTaskRepository taskRepository;
    private final ImageStorageClient imageStorageClient;

    @Scheduled(fixedDelayString = "${deletion.image-retry-interval-ms:60000}")
    @Transactional
    public void deleteScheduledImages() {
        LocalDateTime now = LocalDateTime.now();
        List<ImageDeletionTask> tasks = taskRepository.findReadyForUpdate(
                now,
                PageRequest.of(0, BATCH_SIZE)
        );

        for (ImageDeletionTask task : tasks) {
            try {
                imageStorageClient.deleteObject(task.getObjectKey());
                taskRepository.delete(task);
            } catch (RuntimeException exception) {
                task.markFailed(exception.getMessage(), now);
                log.warn(
                        "Failed to delete image object. objectKey={}, attempts={}",
                        task.getObjectKey(),
                        task.getAttempts(),
                        exception
                );
            }
        }
    }
}
