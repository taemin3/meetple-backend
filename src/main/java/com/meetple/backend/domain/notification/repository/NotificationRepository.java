package com.meetple.backend.domain.notification.repository;

import com.meetple.backend.domain.notification.entity.Notification;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Modifying(flushAutomatically = true)
    @Query("""
            update Notification n
               set n.message = '탈퇴한 회원과 관련된 참여 알림입니다.'
             where n.type in ('PARTICIPATION_APPLIED', 'PARTICIPATION_CANCELED')
               and n.message like concat(:nickname, '님이 %')
            """)
    int anonymizeParticipationActorByNickname(@Param("nickname") String nickname);

    long deleteAllByMemberId(Long memberId);

    @EntityGraph(attributePaths = "member")
    Page<Notification> findByMemberId(Long memberId, Pageable pageable);

    Optional<Notification> findByIdAndMemberId(Long id, Long memberId);
}
