package com.meetple.backend.domain.chat.repository;

import com.meetple.backend.domain.chat.entity.ChatNotificationSetting;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatNotificationSettingRepository
        extends JpaRepository<ChatNotificationSetting, Long> {

    Optional<ChatNotificationSetting> findByMeetingIdAndMemberId(
            Long meetingId,
            Long memberId
    );

    @Query("""
            select setting.member.id
            from ChatNotificationSetting setting
            where setting.meeting.id = :meetingId
              and setting.member.id in :memberIds
              and setting.enabled = false
            """)
    Set<Long> findDisabledMemberIds(
            @Param("meetingId") Long meetingId,
            @Param("memberIds") Collection<Long> memberIds
    );
}
