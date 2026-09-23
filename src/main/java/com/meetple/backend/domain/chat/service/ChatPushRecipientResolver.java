package com.meetple.backend.domain.chat.service;

import com.meetple.backend.domain.meeting.entity.Meeting;
import com.meetple.backend.domain.meeting.entity.ParticipationStatus;
import com.meetple.backend.domain.meeting.repository.MeetingParticipationRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatPushRecipientResolver {

    private final MeetingParticipationRepository participationRepository;

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
        return List.copyOf(recipientMemberIds);
    }
}
