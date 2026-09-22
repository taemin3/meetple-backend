package com.meetple.backend.domain.moderation.repository;

import com.meetple.backend.domain.moderation.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {
}
