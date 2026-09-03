package com.meetple.backend.domain.meeting.repository;

import com.meetple.backend.domain.meeting.entity.Meeting;
import com.meetple.backend.domain.meeting.entity.MeetingStatus;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    List<Meeting> findByHostId(Long hostId);

    @EntityGraph(attributePaths = {"host", "category"})
    Page<Meeting> findByHostId(Long hostId, Pageable pageable);

    long countByHostId(Long hostId);

    @Query(
            value = """
                    select m.*
                    from meetings m
                    where m.deleted_at is null
                      and (
                        m.host_id = :memberId
                        or exists (
                            select 1
                            from meeting_participations p
                            where p.meeting_id = m.id
                              and p.member_id = :memberId
                              and p.status = 'APPROVED'
                        )
                      )
                    order by coalesce((
                        select cm.created_at
                        from chat_messages cm
                        where cm.meeting_id = m.id
                        order by cm.room_sequence desc
                        limit 1
                    ), m.created_at) desc, m.id desc
                    """,
            countQuery = """
                    select count(*)
                    from meetings m
                    where m.deleted_at is null
                      and (
                        m.host_id = :memberId
                        or exists (
                            select 1
                            from meeting_participations p
                            where p.meeting_id = m.id
                              and p.member_id = :memberId
                              and p.status = 'APPROVED'
                        )
                      )
                    """,
            nativeQuery = true
    )
    Page<Meeting> findChatAccessibleMeetings(@Param("memberId") Long memberId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Meeting> findByStatusInAndEndDateLessThanEqual(
            List<MeetingStatus> statuses,
            java.time.LocalDateTime endDate
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Meeting> findByStatusInAndEndDateIsNullAndMeetingDateLessThanEqual(
            List<MeetingStatus> statuses,
            java.time.LocalDateTime meetingDate
    );

    @Override
    @EntityGraph(attributePaths = {"host", "category"})
    Page<Meeting> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"host", "category"})
    Page<Meeting> findByStatus(MeetingStatus status, Pageable pageable);

    @Query(
            value = """
                    select new com.meetple.backend.domain.meeting.repository.MeetingSummaryProjection(
                        m.id,
                        h.id,
                        h.nickname,
                        c.id,
                        c.name,
                        m.title,
                        m.locationName,
                        m.address,
                        m.latitude,
                        m.longitude,
                        m.meetingDate,
                        m.maxPeople,
                        m.currentPeople,
                        m.status,
                        m.thumbnailImageObjectKey,
                        c.defaultImageUrl
                    )
                    from Meeting m
                    join m.host h
                    join m.category c
                    """,
            countQuery = "select count(m) from Meeting m"
    )
    Page<MeetingSummaryProjection> findAllSummaries(Pageable pageable);

    @Query(
            value = """
                    select new com.meetple.backend.domain.meeting.repository.MeetingSummaryProjection(
                        m.id,
                        h.id,
                        h.nickname,
                        c.id,
                        c.name,
                        m.title,
                        m.locationName,
                        m.address,
                        m.latitude,
                        m.longitude,
                        m.meetingDate,
                        m.maxPeople,
                        m.currentPeople,
                        m.status,
                        m.thumbnailImageObjectKey,
                        c.defaultImageUrl
                    )
                    from Meeting m
                    join m.host h
                    join m.category c
                    where m.status = :status
                    """,
            countQuery = "select count(m) from Meeting m where m.status = :status"
    )
    Page<MeetingSummaryProjection> findSummariesByStatus(
            @Param("status") MeetingStatus status,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"host"})
    @Query("select m from Meeting m where m.id = :meetingId")
    Optional<Meeting> findByIdForUpdate(@Param("meetingId") Long meetingId);

    @EntityGraph(attributePaths = {"host", "category"})
    @Query("select m from Meeting m where m.id in :meetingIds")
    List<Meeting> findAllWithHostAndCategoryByIdIn(@Param("meetingIds") Collection<Long> meetingIds);

    @Query(
            value = """
                select m.id
                from meetings m
                join categories c on c.id = m.category_id
                where m.status = :status
                  and m.deleted_at is null
                  and (
                        lower(m.title) like :keywordPattern escape '!'
                        or lower(m.location_name) like :keywordPattern escape '!'
                        or lower(m.address) like :keywordPattern escape '!'
                      )
                  and (:categoryName is null or c.name = :categoryName)
                order by (:earthRadiusMeters * acos(least(1.0, greatest(-1.0,
                        cos(radians(:latitude)) * cos(radians(m.latitude))
                        * cos(radians(m.longitude) - radians(:longitude))
                        + sin(radians(:latitude)) * sin(radians(m.latitude))
                      )))) asc,
                      m.meeting_date asc,
                      m.id asc
                """,
            countQuery = """
                select count(*)
                from meetings m
                join categories c on c.id = m.category_id
                where m.status = :status
                  and m.deleted_at is null
                  and (
                        lower(m.title) like :keywordPattern escape '!'
                        or lower(m.location_name) like :keywordPattern escape '!'
                        or lower(m.address) like :keywordPattern escape '!'
                      )
                  and (:categoryName is null or c.name = :categoryName)
                """,
            nativeQuery = true
    )
    Page<Long> searchMeetingIds(
            @Param("status") String status,
            @Param("keywordPattern") String keywordPattern,
            @Param("categoryName") String categoryName,
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("earthRadiusMeters") double earthRadiusMeters,
            Pageable pageable
    );

    @Query(
            value = """
                    select m.*
                    from meetings m
                    join categories c on c.id = m.category_id
                    where m.status = :status
                      and m.deleted_at is null
                      and m.latitude between :minLatitude and :maxLatitude
                      and (
                            (:crossesAntimeridian = false and m.longitude between :minLongitude and :maxLongitude)
                            or (:crossesAntimeridian = true and (m.longitude >= :minLongitude or m.longitude <= :maxLongitude))
                          )
                      and (:categoryName is null or c.name = :categoryName)
                      and (:earthRadiusMeters * acos(least(1.0, greatest(-1.0,
                            cos(radians(:latitude)) * cos(radians(m.latitude))
                            * cos(radians(m.longitude) - radians(:longitude))
                            + sin(radians(:latitude)) * sin(radians(m.latitude))
                          )))) <= :radiusMeters
                    order by (:earthRadiusMeters * acos(least(1.0, greatest(-1.0,
                            cos(radians(:latitude)) * cos(radians(m.latitude))
                            * cos(radians(m.longitude) - radians(:longitude))
                            + sin(radians(:latitude)) * sin(radians(m.latitude))
                          )))) asc
                    """,
            countQuery = """
                    select count(*)
                    from meetings m
                    join categories c on c.id = m.category_id
                    where m.status = :status
                      and m.deleted_at is null
                      and m.latitude between :minLatitude and :maxLatitude
                      and (
                            (:crossesAntimeridian = false and m.longitude between :minLongitude and :maxLongitude)
                            or (:crossesAntimeridian = true and (m.longitude >= :minLongitude or m.longitude <= :maxLongitude))
                          )
                      and (:categoryName is null or c.name = :categoryName)
                      and (:earthRadiusMeters * acos(least(1.0, greatest(-1.0,
                            cos(radians(:latitude)) * cos(radians(m.latitude))
                            * cos(radians(m.longitude) - radians(:longitude))
                            + sin(radians(:latitude)) * sin(radians(m.latitude))
                          )))) <= :radiusMeters
                    """,
            nativeQuery = true
    )
    Page<Meeting> findNearbyMeetings(
            @Param("status") String status,
            @Param("minLatitude") BigDecimal minLatitude,
            @Param("maxLatitude") BigDecimal maxLatitude,
            @Param("minLongitude") BigDecimal minLongitude,
            @Param("maxLongitude") BigDecimal maxLongitude,
            @Param("crossesAntimeridian") boolean crossesAntimeridian,
            @Param("categoryName") String categoryName,
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radiusMeters") int radiusMeters,
            @Param("earthRadiusMeters") double earthRadiusMeters,
            Pageable pageable
    );

    @Query(
            value = """
                    select m.id
                    from meetings m
                    where m.deleted_at is not null
                      and m.deleted_at <= :cutoff
                    order by m.deleted_at asc, m.id asc
                    """,
            nativeQuery = true
    )
    List<Long> findPurgeCandidateIds(@Param("cutoff") LocalDateTime cutoff, Pageable pageable);

    @Modifying
    @Query(value = "delete from meetings where id in (:meetingIds) and deleted_at is not null", nativeQuery = true)
    int deletePermanentlyByIdIn(@Param("meetingIds") Collection<Long> meetingIds);
}
