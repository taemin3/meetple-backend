package com.meetple.backend.domain.moderation.repository;

import com.meetple.backend.domain.moderation.entity.Report;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select report from Report report where report.id = :reportId")
    Optional<Report> findByIdForUpdate(@Param("reportId") Long reportId);
}
