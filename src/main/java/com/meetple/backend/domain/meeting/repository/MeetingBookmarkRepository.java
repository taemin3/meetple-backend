package com.meetple.backend.domain.meeting.repository;

import com.meetple.backend.domain.meeting.entity.MeetingBookmark;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetingBookmarkRepository extends JpaRepository<MeetingBookmark, Long> {

    boolean existsByMeetingIdAndMemberId(Long meetingId, Long memberId);

    Optional<MeetingBookmark> findByMeetingIdAndMemberId(Long meetingId, Long memberId);

    void deleteByMeetingId(Long meetingId);

    @EntityGraph(attributePaths = {"meeting", "meeting.host", "meeting.category"})
    @Query(
            value = """
                    select bookmark
                    from MeetingBookmark bookmark
                    where bookmark.member.id = :memberId
                      and bookmark.meeting.deletedAt is null
                    """,
            countQuery = """
                    select count(bookmark)
                    from MeetingBookmark bookmark
                    where bookmark.member.id = :memberId
                      and bookmark.meeting.deletedAt is null
                    """
    )
    Page<MeetingBookmark> findByMemberId(@Param("memberId") Long memberId, Pageable pageable);

    @Query("""
            select count(bookmark)
            from MeetingBookmark bookmark
            where bookmark.member.id = :memberId
              and bookmark.meeting.deletedAt is null
            """)
    long countByMemberId(@Param("memberId") Long memberId);

    @Modifying
    @Query(value = "delete from meeting_bookmarks where meeting_id in (:meetingIds)", nativeQuery = true)
    int deleteByMeetingIdInIncludingDeletedMeetings(@Param("meetingIds") Collection<Long> meetingIds);
}
