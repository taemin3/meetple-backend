package com.meetple.backend.domain.legal.repository;

import com.meetple.backend.domain.legal.entity.MemberLegalRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberLegalRecordRepository extends JpaRepository<MemberLegalRecord, Long> {
}
