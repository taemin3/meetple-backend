package com.meetple.backend.domain.chat.service;

import com.meetple.backend.domain.chat.dto.request.UpdateChatNotificationSettingRequest;
import com.meetple.backend.domain.chat.dto.response.ChatNotificationSettingResponse;
import com.meetple.backend.domain.chat.entity.ChatNotificationSetting;
import com.meetple.backend.domain.chat.repository.ChatNotificationSettingRepository;
import com.meetple.backend.domain.meeting.entity.Meeting;
import com.meetple.backend.domain.meeting.repository.MeetingRepository;
import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.repository.MemberRepository;
import com.meetple.backend.global.exception.NotFoundException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatNotificationSettingService {

    private final ChatNotificationSettingRepository settingRepository;
    private final MemberRepository memberRepository;
    private final ChatAccessPolicy accessPolicy;
    private final MeetingRepository meetingRepository;

    public ChatNotificationSettingResponse get(Long memberId, Long meetingId) {
        accessPolicy.getAccessibleMeeting(memberId, meetingId);
        boolean enabled = settingRepository.findByMeetingIdAndMemberId(meetingId, memberId)
                .map(ChatNotificationSetting::isEnabled)
                .orElse(true);
        return new ChatNotificationSettingResponse(meetingId, enabled);
    }

    @Transactional
    public ChatNotificationSettingResponse update(
            Long memberId,
            Long meetingId,
            UpdateChatNotificationSettingRequest request
    ) {
        Meeting meeting = meetingRepository.findByIdForUpdate(meetingId)
                .orElseThrow(() -> new NotFoundException("모임을 찾을 수 없습니다."));
        accessPolicy.ensureCanAccess(memberId, meeting);
        ChatNotificationSetting setting = settingRepository
                .findByMeetingIdAndMemberId(meetingId, memberId)
                .orElseGet(() -> ChatNotificationSetting.create(
                        meeting,
                        getMember(memberId),
                        request.enabled()
                ));
        setting.update(request.enabled());
        settingRepository.save(setting);
        return new ChatNotificationSettingResponse(meetingId, setting.isEnabled());
    }

    public List<Long> filterPushEnabledRecipients(
            Long meetingId,
            Collection<Long> recipientMemberIds
    ) {
        if (recipientMemberIds.isEmpty()) {
            return List.of();
        }
        Set<Long> disabledMemberIds = settingRepository.findDisabledMemberIds(
                meetingId,
                recipientMemberIds
        );
        return recipientMemberIds.stream()
                .filter(memberId -> !disabledMemberIds.contains(memberId))
                .distinct()
                .toList();
    }

    private Member getMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다."));
    }
}
