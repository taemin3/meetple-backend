package com.meetple.backend.domain.meeting.repository;

import com.meetple.backend.domain.meeting.entity.Meeting;
import com.meetple.backend.domain.meeting.entity.MeetingStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    List<Meeting> findByHostId(Long hostId);

    Page<Meeting> findByStatus(MeetingStatus status, Pageable pageable);
}
