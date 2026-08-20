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

    @Query(
            value = """
                    select object_key
                    from meeting_images
                    where meeting_id in (:meetingIds)
                      and object_key is not null
                    union
                    select thumbnail_image_object_key
                    from meetings
                    where id in (:meetingIds)
                      and thumbnail_image_object_key is not null
                    """,
            nativeQuery = true
    )
    List<String> findObjectKeysIncludingDeletedMeetings(@Param("meetingIds") Collection<Long> meetingIds);

    @Modifying
    @Query(value = "delete from meeting_images where meeting_id in (:meetingIds)", nativeQuery = true)
    int deleteByMeetingIdInIncludingDeletedMeetings(@Param("meetingIds") Collection<Long> meetingIds);
}
