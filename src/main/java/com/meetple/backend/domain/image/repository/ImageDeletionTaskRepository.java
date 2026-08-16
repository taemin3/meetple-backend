package com.meetple.backend.domain.image.repository;

import com.meetple.backend.domain.image.entity.ImageDeletionTask;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ImageDeletionTaskRepository extends JpaRepository<ImageDeletionTask, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select task
            from ImageDeletionTask task
            where task.nextAttemptAt <= :now
            order by task.nextAttemptAt asc, task.id asc
            """)
    List<ImageDeletionTask> findReadyForUpdate(@Param("now") LocalDateTime now, Pageable pageable);
}
