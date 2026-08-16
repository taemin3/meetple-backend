package com.meetple.backend.domain.image.service;

import com.meetple.backend.domain.image.entity.ImageDeletionTask;
import com.meetple.backend.domain.image.repository.ImageDeletionTaskRepository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ImageDeletionService {

    private final ImageDeletionTaskRepository taskRepository;

    public void schedule(String objectKey) {
        schedule(StringUtils.hasText(objectKey) ? List.of(objectKey) : List.of());
    }

    public void schedule(Collection<String> objectKeys) {
        LocalDateTime now = LocalDateTime.now();
        List<ImageDeletionTask> tasks = objectKeys.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .map(objectKey -> ImageDeletionTask.create(objectKey, now))
                .toList();
        if (!tasks.isEmpty()) {
            taskRepository.saveAll(tasks);
        }
    }
}
