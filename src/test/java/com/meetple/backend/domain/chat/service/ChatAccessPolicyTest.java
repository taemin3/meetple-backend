package com.meetple.backend.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.meetple.backend.domain.category.entity.Category;
import com.meetple.backend.domain.meeting.entity.Meeting;
import com.meetple.backend.domain.meeting.entity.ParticipationStatus;
import com.meetple.backend.domain.meeting.repository.MeetingParticipationRepository;
import com.meetple.backend.domain.meeting.repository.MeetingRepository;
import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.global.exception.BadRequestException;
import com.meetple.backend.global.exception.ForbiddenException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChatAccessPolicyTest {

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private MeetingParticipationRepository participationRepository;

    @InjectMocks
    private ChatAccessPolicy accessPolicy;

    @Test
    void hostCanAccessChatRoom() {
        Meeting meeting = meeting(10L, member(1L, "host"));

        assertThat(accessPolicy.canAccess(1L, meeting)).isTrue();
    }

    @Test
    void approvedParticipantCanAccessChatRoom() {
        Meeting meeting = meeting(10L, member(1L, "host"));
        given(participationRepository.existsByMeetingIdAndMemberIdAndStatus(
                10L,
                2L,
                ParticipationStatus.APPROVED
        )).willReturn(true);

        assertThat(accessPolicy.canAccess(2L, meeting)).isTrue();
    }

    @Test
    void nonApprovedParticipantCannotAccessChatRoom() {
        Meeting meeting = meeting(10L, member(1L, "host"));

        assertThatThrownBy(() -> accessPolicy.ensureCanAccess(2L, meeting))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("모임 주최자와 승인된 참여자만 채팅방에 입장할 수 있습니다.");
    }

    @Test
    void completedMeetingChatIsReadOnly() {
        Meeting meeting = meeting(10L, member(1L, "host"));
        meeting.complete();

        assertThatThrownBy(() -> accessPolicy.ensureCanSend(meeting))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("종료되거나 취소된 모임의 채팅방에서는 메시지를 보낼 수 없습니다.");
    }

    private Meeting meeting(Long id, Member host) {
        Category category = Category.create("exercise");
        ReflectionTestUtils.setField(category, "id", 1L);
        Meeting meeting = Meeting.create(
                host,
                category,
                "Weekend running",
                "Run together.",
                "Yeouido Park",
                "Seoul",
                new BigDecimal("37.521900"),
                new BigDecimal("126.924500"),
                10,
                LocalDateTime.now().plusDays(1),
                null
        );
        ReflectionTestUtils.setField(meeting, "id", id);
        return meeting;
    }

    private Member member(Long id, String nickname) {
        Member member = Member.createUser(
                nickname + "@meetple.com",
                "encoded-password",
                nickname,
                "Seoul"
        );
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
