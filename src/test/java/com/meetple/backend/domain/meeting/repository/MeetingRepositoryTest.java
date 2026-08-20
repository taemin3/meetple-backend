package com.meetple.backend.domain.meeting.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meetple.backend.domain.category.entity.Category;
import com.meetple.backend.domain.category.repository.CategoryRepository;
import com.meetple.backend.domain.meeting.entity.Meeting;
import com.meetple.backend.domain.meeting.entity.MeetingBookmark;
import com.meetple.backend.domain.meeting.entity.MeetingImage;
import com.meetple.backend.domain.meeting.entity.MeetingParticipation;
import com.meetple.backend.domain.meeting.entity.MeetingStatus;
import com.meetple.backend.domain.meeting.entity.ParticipationStatus;
import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.repository.MemberRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.Lock;
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

    @Autowired
    private MeetingBookmarkRepository bookmarkRepository;

    @Autowired
    private MeetingImageRepository meetingImageRepository;

    @Autowired
    private EntityManager entityManager;

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
    void findNearbyMeetingsFiltersByDistanceAndAppliesPaging() {
        Member host = memberRepository.save(Member.createUser(
                "nearby-host@meetple.com",
                "encoded-password",
                "host",
                "Seoul"
        ));
        Category category = categoryRepository.save(Category.create("exercise"));
        Meeting nearby = meetingRepository.save(createMeeting(
                host,
                category,
                "Nearby running",
                new BigDecimal("37.521900"),
                new BigDecimal("126.924500")
        ));
        meetingRepository.save(createMeeting(
                host,
                category,
                "Far running",
                new BigDecimal("37.540000"),
                new BigDecimal("126.924500")
        ));

        Page<Meeting> result = meetingRepository.findNearbyMeetings(
                MeetingStatus.RECRUITING.name(),
                new BigDecimal("37.500000"),
                new BigDecimal("37.550000"),
                new BigDecimal("126.900000"),
                new BigDecimal("126.950000"),
                false,
                "exercise",
                37.5219,
                126.9245,
                1000,
                6371000.0,
                PageRequest.of(0, 1)
        );

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).extracting(Meeting::getId)
                .containsExactly(nearby.getId());
    }

    @Test
    void searchMeetingsReturnsGlobalKeywordMatchesInDistanceOrder() {
        Member host = memberRepository.save(Member.createUser(
                "search-host@meetple.com",
                "encoded-password",
                "host",
                "Seoul"
        ));
        Category exercise = categoryRepository.save(Category.create("exercise"));
        Category study = categoryRepository.save(Category.create("study"));
        Meeting nearby = meetingRepository.save(createMeeting(
                host,
                exercise,
                "Nearby 100% running",
                new BigDecimal("37.521900"),
                new BigDecimal("126.924500")
        ));
        Meeting farAway = meetingRepository.save(createMeeting(
                host,
                exercise,
                "Busan 100% running",
                new BigDecimal("35.179600"),
                new BigDecimal("129.075600")
        ));
        meetingRepository.save(createMeeting(
                host,
                exercise,
                "Nearby 1000 running",
                new BigDecimal("37.520000"),
                new BigDecimal("126.924500")
        ));
        meetingRepository.save(createMeeting(
                host,
                study,
                "Closer 100% running study",
                new BigDecimal("37.521000"),
                new BigDecimal("126.924500")
        ));
        Meeting completed = meetingRepository.save(createMeeting(
                host,
                exercise,
                "Completed 100% running",
                new BigDecimal("37.521500"),
                new BigDecimal("126.924500")
        ));
        completed.complete();
        meetingRepository.flush();
        entityManager.clear();

        Page<Long> result = meetingRepository.searchMeetingIds(
                MeetingStatus.RECRUITING.name(),
                "%100!% running%",
                "exercise",
                37.5219,
                126.9245,
                6371000.0,
                PageRequest.of(0, 20)
        );

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).containsExactly(nearby.getId(), farAway.getId());

        List<Meeting> meetings = meetingRepository.findAllWithHostAndCategoryByIdIn(result.getContent());
        assertThat(meetings).allSatisfy(meeting -> {
            assertThat(Hibernate.isInitialized(meeting.getHost())).isTrue();
            assertThat(Hibernate.isInitialized(meeting.getCategory())).isTrue();
        });
    }

    @Test
    void softDeletedMeetingIsExcludedFromEntitySearchAndNearbyQueries() {
        Member host = memberRepository.save(Member.createUser(
                "deleted-host@meetple.com",
                "encoded-password",
                "host",
                "Seoul"
        ));
        Category category = categoryRepository.save(Category.create("exercise"));
        Meeting deleted = meetingRepository.save(createMeeting(
                host,
                category,
                "Deleted running",
                new BigDecimal("37.521900"),
                new BigDecimal("126.924500")
        ));
        Long deletedId = deleted.getId();
        bookmarkRepository.save(MeetingBookmark.create(deleted, host));
        deleted.softDelete(LocalDateTime.now());
        meetingRepository.flush();
        entityManager.clear();

        assertThat(meetingRepository.findById(deletedId)).isEmpty();
        assertThat(meetingRepository.findByHostId(host.getId())).isEmpty();
        assertThat(bookmarkRepository.countByMemberId(host.getId())).isZero();
        assertThat(meetingRepository.searchMeetingIds(
                MeetingStatus.RECRUITING.name(),
                "%deleted%",
                "exercise",
                37.5219,
                126.9245,
                6371000.0,
                PageRequest.of(0, 20)
        )).isEmpty();
        assertThat(meetingRepository.findNearbyMeetings(
                MeetingStatus.RECRUITING.name(),
                new BigDecimal("37.500000"),
                new BigDecimal("37.550000"),
                new BigDecimal("126.900000"),
                new BigDecimal("126.950000"),
                false,
                "exercise",
                37.5219,
                126.9245,
                1000,
                6371000.0,
                PageRequest.of(0, 20)
        )).isEmpty();
    }

    @Test
    void permanentlyDeletesExpiredMeetingAndReturnsAllStoredImageKeys() {
        Member host = memberRepository.save(Member.createUser(
                "purge-host@meetple.com",
                "encoded-password",
                "host",
                "Seoul"
        ));
        Category category = categoryRepository.save(Category.create("exercise"));
        Meeting meeting = meetingRepository.save(Meeting.create(
                host,
                category,
                "Expired meeting",
                "Meeting scheduled for permanent deletion.",
                "Yeouido Park",
                "330 Yeouidong-ro, Seoul",
                new BigDecimal("37.521900"),
                new BigDecimal("126.924500"),
                10,
                LocalDateTime.now().plusDays(7),
                "images/meeting/1/thumbnail.png"
        ));
        meetingImageRepository.save(MeetingImage.create(
                meeting,
                "images/meeting/1/detail.png",
                0
        ));
        bookmarkRepository.save(MeetingBookmark.create(meeting, host));
        meeting.softDelete(LocalDateTime.now().minusDays(31));
        meetingRepository.flush();
        entityManager.clear();

        List<Long> candidateIds = meetingRepository.findPurgeCandidateIds(
                LocalDateTime.now().minusDays(30),
                PageRequest.of(0, 20)
        );

        assertThat(candidateIds).containsExactly(meeting.getId());
        assertThat(meetingImageRepository.findObjectKeysIncludingDeletedMeetings(candidateIds))
                .containsExactlyInAnyOrder(
                        "images/meeting/1/thumbnail.png",
                        "images/meeting/1/detail.png"
                );
        bookmarkRepository.deleteByMeetingIdInIncludingDeletedMeetings(candidateIds);
        meetingImageRepository.deleteByMeetingIdInIncludingDeletedMeetings(candidateIds);
        assertThat(meetingRepository.deletePermanentlyByIdIn(candidateIds)).isEqualTo(1);
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

    @Test
    void completionQueriesUsePessimisticWriteLock() throws NoSuchMethodException {
        assertThat(MeetingRepository.class.getMethod(
                "findByStatusInAndEndDateLessThanEqual",
                List.class,
                LocalDateTime.class
        ).getAnnotation(Lock.class).value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(MeetingRepository.class.getMethod(
                "findByStatusInAndEndDateIsNullAndMeetingDateLessThanEqual",
                List.class,
                LocalDateTime.class
        ).getAnnotation(Lock.class).value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
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

    private Meeting createMeeting(
            Member host,
            Category category,
            String title,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        return Meeting.create(
                host,
                category,
                title,
                "Run together at an easy pace.",
                "Yeouido Park",
                "330 Yeouidong-ro, Yeongdeungpo-gu, Seoul",
                latitude,
                longitude,
                10,
                LocalDateTime.now().plusDays(7),
                null
        );
    }
}
