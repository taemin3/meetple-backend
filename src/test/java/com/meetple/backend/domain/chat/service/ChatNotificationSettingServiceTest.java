package com.meetple.backend.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.meetple.backend.domain.chat.dto.request.UpdateChatNotificationSettingRequest;
import com.meetple.backend.domain.chat.entity.ChatNotificationSetting;
import com.meetple.backend.domain.chat.repository.ChatNotificationSettingRepository;
import com.meetple.backend.domain.meeting.entity.Meeting;
import com.meetple.backend.domain.meeting.repository.MeetingRepository;
import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.repository.MemberRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatNotificationSettingServiceTest {

    @Mock
    private ChatNotificationSettingRepository settingRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ChatAccessPolicy accessPolicy;

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private Meeting meeting;

    @Mock
    private Member member;

    @InjectMocks
    private ChatNotificationSettingService service;

    @Test
    void defaultsToEnabledWhenNoSettingExists() {
        given(accessPolicy.getAccessibleMeeting(1L, 10L)).willReturn(meeting);
        given(settingRepository.findByMeetingIdAndMemberId(10L, 1L))
                .willReturn(Optional.empty());

        var response = service.get(1L, 10L);

        assertThat(response.roomId()).isEqualTo(10L);
        assertThat(response.enabled()).isTrue();
    }

    @Test
    void createsDisabledSettingForAccessibleMember() {
        given(meetingRepository.findByIdForUpdate(10L)).willReturn(Optional.of(meeting));
        given(settingRepository.findByMeetingIdAndMemberId(10L, 1L))
                .willReturn(Optional.empty());
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        var response = service.update(
                1L,
                10L,
                new UpdateChatNotificationSettingRequest(false)
        );

        ArgumentCaptor<ChatNotificationSetting> captor =
                ArgumentCaptor.forClass(ChatNotificationSetting.class);
        verify(settingRepository).save(captor.capture());
        assertThat(captor.getValue().getMeeting()).isSameAs(meeting);
        assertThat(captor.getValue().getMember()).isSameAs(member);
        assertThat(captor.getValue().isEnabled()).isFalse();
        assertThat(response.enabled()).isFalse();
        verify(accessPolicy).ensureCanAccess(1L, meeting);
    }

    @Test
    void filtersOnlyRecipientsWhoDisabledThisRoom() {
        given(settingRepository.findDisabledMemberIds(10L, List.of(1L, 2L, 3L)))
                .willReturn(Set.of(2L));

        var recipients = service.filterPushEnabledRecipients(
                10L,
                List.of(1L, 2L, 3L)
        );

        assertThat(recipients).containsExactly(1L, 3L);
    }
}
