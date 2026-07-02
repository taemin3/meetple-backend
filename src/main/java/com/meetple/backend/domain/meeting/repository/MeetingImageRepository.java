package com.meetple.backend.domain.meeting.repository;

import com.meetple.backend.domain.meeting.entity.MeetingImage;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetingImageRepository extends JpaRepository<MeetingImage, Long> {

    List<MeetingImage> findByMeetingIdOrderBySortOrderAsc(Long meetingId);

    List<MeetingImage> findByMeetingIdInOrderByMeetingIdAscSortOrderAsc(Collection<Long> meetingIds);

    @Modifying
    @Query("delete from MeetingImage image where image.meeting.id = :meetingId")
    void deleteByMeetingId(@Param("meetingId") Long meetingId);
}
