package com.meetple.backend.domain.meeting.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meetple.backend.domain.category.entity.Category;
import com.meetple.backend.domain.category.repository.CategoryRepository;
import com.meetple.backend.domain.meeting.entity.Meeting;
import com.meetple.backend.domain.meeting.entity.MeetingParticipation;
import com.meetple.backend.domain.meeting.entity.MeetingStatus;
import com.meetple.backend.domain.meeting.entity.ParticipationStatus;
import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.repository.MemberRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class MeetingRepositoryTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private MeetingParticipationRepository participationRepository;

    @Test
    void saveMeetingWithHostAndCategory() {
        Member host = memberRepository.save(Member.createUser(
                "host@meetple.com",
                "encoded-password",
                "호스트",
                "서울"
        ));
        Category category = categoryRepository.save(Category.create("운동"));

        Meeting meeting = meetingRepository.save(createMeeting(host, category));

        assertThat(meeting.getId()).isNotNull();
        assertThat(meeting.getHost().getId()).isEqualTo(host.getId());
        assertThat(meeting.getCategory().getId()).isEqualTo(category.getId());
        assertThat(meeting.getCurrentPeople()).isEqualTo(1);
        assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.RECRUITING);
        assertThat(meeting.getCreatedAt()).isNotNull();
        assertThat(meeting.getUpdatedAt()).isNotNull();
        assertThat(meetingRepository.findByHostId(host.getId())).hasSize(1);
    }

    @Test
    void preventDuplicateParticipationForSameMeetingAndMember() {
        Member host = memberRepository.save(Member.createUser(
                "host@meetple.com",
                "encoded-password",
                "호스트",
                "서울"
        ));
        Member participant = memberRepository.save(Member.createUser(
                "runner@meetple.com",
                "encoded-password",
                "러너",
                "서울"
        ));
        Category category = categoryRepository.save(Category.create("운동"));
        Meeting meeting = meetingRepository.save(createMeeting(host, category));

        participationRepository.save(MeetingParticipation.apply(meeting, participant, "같이 뛰고 싶어요."));
        participationRepository.flush();

        assertThatThrownBy(() -> {
            participationRepository.save(MeetingParticipation.apply(meeting, participant, "다시 신청합니다."));
            participationRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void participationStatusTransitions() {
        Member host = memberRepository.save(Member.createUser(
                "host@meetple.com",
                "encoded-password",
                "호스트",
                "서울"
        ));
        Member participant = memberRepository.save(Member.createUser(
                "runner@meetple.com",
                "encoded-password",
                "러너",
                "서울"
        ));
        Category category = categoryRepository.save(Category.create("운동"));
        Meeting meeting = meetingRepository.save(createMeeting(host, category));
        MeetingParticipation participation = participationRepository.save(
                MeetingParticipation.apply(meeting, participant, "참여하고 싶어요.")
        );

        participation.approve();

        assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.APPROVED);
        assertThat(participation.getReviewedAt()).isNotNull();
        assertThat(participationRepository.existsByMeetingIdAndMemberId(
                meeting.getId(),
                participant.getId()
        )).isTrue();
    }

    private Meeting createMeeting(Member host, Category category) {
        return Meeting.create(
                host,
                category,
                "주말 러닝 모임",
                "가볍게 뛰고 커피 마셔요.",
                "한강공원",
                "서울 영등포구 여의동로 330",
                new BigDecimal("37.521900"),
                new BigDecimal("126.924500"),
                10,
                LocalDateTime.now().plusDays(7),
                null
        );
    }
}
