package com.meetple.backend.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.meetple.backend.domain.category.entity.Category;
import com.meetple.backend.domain.meeting.entity.Meeting;
import com.meetple.backend.domain.meeting.entity.MeetingParticipation;
import com.meetple.backend.domain.meeting.entity.ParticipationStatus;
import com.meetple.backend.domain.meeting.repository.MeetingParticipationRepository;
import com.meetple.backend.domain.member.entity.Member;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChatPushRecipientResolverTest {

    @Mock
    private MeetingParticipationRepository participationRepository;

    @InjectMocks
    private ChatPushRecipientResolver resolver;

    @Test
    void resolvesHostAndApprovedParticipantsWithoutSenderOrDuplicates() {
        Member host = member(1L, "host");
        Member sender = member(2L, "sender");
        Member participant = member(3L, "participant");
        Meeting meeting = meeting(10L, host);
        given(participationRepository.findByMeetingIdAndStatus(
                10L,
                ParticipationStatus.APPROVED
        )).willReturn(List.of(
                approvedParticipation(meeting, sender),
                approvedParticipation(meeting, participant)
        ));

        assertThat(resolver.resolve(meeting, sender.getId()))
                .containsExactly(host.getId(), participant.getId());
    }

    @Test
    void returnsEmptyWhenSenderIsTheOnlyAccessibleMember() {
        Member host = member(1L, "host");
        Meeting meeting = meeting(10L, host);
        given(participationRepository.findByMeetingIdAndStatus(
                10L,
                ParticipationStatus.APPROVED
        )).willReturn(List.of());

        assertThat(resolver.resolve(meeting, host.getId())).isEmpty();
    }

    private MeetingParticipation approvedParticipation(Meeting meeting, Member member) {
        MeetingParticipation participation = MeetingParticipation.apply(meeting, member, null);
        participation.approve();
        return participation;
    }

    private Meeting meeting(Long id, Member host) {
        Category category = Category.create("exercise");
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
