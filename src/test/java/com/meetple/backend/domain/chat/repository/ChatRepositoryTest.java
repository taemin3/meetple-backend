package com.meetple.backend.domain.chat.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meetple.backend.domain.category.entity.Category;
import com.meetple.backend.domain.category.repository.CategoryRepository;
import com.meetple.backend.domain.chat.entity.ChatMessage;
import com.meetple.backend.domain.meeting.entity.Meeting;
import com.meetple.backend.domain.meeting.entity.MeetingParticipation;
import com.meetple.backend.domain.meeting.repository.MeetingParticipationRepository;
import com.meetple.backend.domain.meeting.repository.MeetingRepository;
import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.repository.MemberRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class ChatRepositoryTest {

    @Autowired
    private ChatMessageRepository messageRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private MeetingParticipationRepository participationRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    void roomListIncludesHostedAndApprovedMeetingsOnly() {
        Member member = memberRepository.save(member("member"));
        Member otherHost = memberRepository.save(member("other-host"));
        Category category = categoryRepository.save(Category.create("exercise"));
        Meeting hosted = meetingRepository.save(meeting(member, category, "hosted"));
        Meeting approved = meetingRepository.save(meeting(otherHost, category, "approved"));
        Meeting pending = meetingRepository.save(meeting(otherHost, category, "pending"));

        MeetingParticipation approvedParticipation = MeetingParticipation.apply(approved, member, null);
        approvedParticipation.approve();
        participationRepository.save(approvedParticipation);
        participationRepository.save(MeetingParticipation.apply(pending, member, null));
        participationRepository.flush();

        var result = meetingRepository.findChatAccessibleMeetings(
                member.getId(),
                PageRequest.of(0, 20)
        );

        assertThat(result.getContent()).extracting(Meeting::getId)
                .containsExactlyInAnyOrder(hosted.getId(), approved.getId())
                .doesNotContain(pending.getId());
    }

    @Test
    void duplicateClientMessageIdIsRejectedWithinSameRoomAndSender() {
        Member host = memberRepository.save(member("host"));
        Category category = categoryRepository.save(Category.create("exercise"));
        Meeting meeting = meetingRepository.save(meeting(host, category, "running"));
        UUID clientMessageId = UUID.randomUUID();
        messageRepository.save(ChatMessage.create(
                meeting,
                host,
                1L,
                clientMessageId,
                "first"
        ));
        messageRepository.flush();

        assertThatThrownBy(() -> {
            messageRepository.save(ChatMessage.create(
                    meeting,
                    host,
                    2L,
                    clientMessageId,
                    "duplicate"
            ));
            messageRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void cursorQueriesRespectRoomSequenceOrder() {
        Member host = memberRepository.save(member("host"));
        Category category = categoryRepository.save(Category.create("exercise"));
        Meeting meeting = meetingRepository.save(meeting(host, category, "running"));
        for (long sequence = 1; sequence <= 4; sequence++) {
            messageRepository.save(ChatMessage.create(
                    meeting,
                    host,
                    sequence,
                    UUID.randomUUID(),
                    "message-" + sequence
            ));
        }
        messageRepository.flush();

        var older = messageRepository
                .findByMeetingIdAndRoomSequenceLessThanOrderByRoomSequenceDesc(
                        meeting.getId(),
                        4L,
                        PageRequest.of(0, 2)
                );
        var newer = messageRepository
                .findByMeetingIdAndRoomSequenceGreaterThanOrderByRoomSequenceAsc(
                        meeting.getId(),
                        2L,
                        PageRequest.of(0, 2)
                );

        assertThat(older).extracting(ChatMessage::getRoomSequence).containsExactly(3L, 2L);
        assertThat(newer).extracting(ChatMessage::getRoomSequence).containsExactly(3L, 4L);
    }

    private Member member(String nickname) {
        return Member.createUser(
                nickname + "@meetple.com",
                "encoded-password",
                nickname,
                "Seoul"
        );
    }

    private Meeting meeting(Member host, Category category, String title) {
        return Meeting.create(
                host,
                category,
                title,
                "Run together.",
                "Yeouido Park",
                "Seoul",
                new BigDecimal("37.521900"),
                new BigDecimal("126.924500"),
                10,
                LocalDateTime.now().plusDays(1),
                null
        );
    }
}
