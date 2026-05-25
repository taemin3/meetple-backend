package com.meetple.backend.domain.meeting.repository;

import com.meetple.backend.domain.meeting.entity.Meeting;
import com.meetple.backend.domain.meeting.entity.MeetingStatus;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    List<Meeting> findByHostId(Long hostId);

    Page<Meeting> findByStatus(MeetingStatus status, Pageable pageable);

    @Query("""
            select m
            from Meeting m
            join fetch m.host
            join fetch m.category
            where m.status = :status
              and m.latitude between :minLatitude and :maxLatitude
              and m.longitude between :minLongitude and :maxLongitude
              and (:categoryName is null or m.category.name = :categoryName)
            """)
    List<Meeting> findByStatusAndCoordinateBounds(
            @Param("status") MeetingStatus status,
            @Param("minLatitude") BigDecimal minLatitude,
            @Param("maxLatitude") BigDecimal maxLatitude,
            @Param("minLongitude") BigDecimal minLongitude,
            @Param("maxLongitude") BigDecimal maxLongitude,
            @Param("categoryName") String categoryName
    );
}
