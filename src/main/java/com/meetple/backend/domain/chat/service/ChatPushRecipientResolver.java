package com.meetple.backend.domain.chat.service;

import com.meetple.backend.domain.meeting.entity.Meeting;
import com.meetple.backend.domain.meeting.entity.ParticipationStatus;
import com.meetple.backend.domain.meeting.repository.MeetingParticipationRepository;
import com.meetple.backend.domain.moderation.repository.MemberBlockRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatPushRecipientResolver {

    private final MeetingParticipationRepository participationRepository;
    private final MemberBlockRepository memberBlockRepository;

    public List<Long> resolve(Meeting meeting, Long senderMemberId) {
        Set<Long> recipientMemberIds = new LinkedHashSet<>();
        recipientMemberIds.add(meeting.getHost().getId());
        participationRepository.findByMeetingIdAndStatus(
                        meeting.getId(),
                        ParticipationStatus.APPROVED
                ).stream()
                .map(participation -> participation.getMember().getId())
                .forEach(recipientMemberIds::add);
        recipientMemberIds.remove(senderMemberId);
        if (!recipientMemberIds.isEmpty()) {
            recipientMemberIds.removeAll(memberBlockRepository.findBlockerIdsBlockingMember(
                    senderMemberId,
                    recipientMemberIds
            ));
        }
        return List.copyOf(recipientMemberIds);
    }
}
