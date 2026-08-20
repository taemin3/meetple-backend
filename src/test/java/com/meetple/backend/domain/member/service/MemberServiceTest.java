package com.meetple.backend.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.meetple.backend.domain.image.entity.ImageUploadPurpose;
import com.meetple.backend.domain.image.service.ImageService;
import com.meetple.backend.domain.image.service.ImageDeletionService;
import com.meetple.backend.domain.member.dto.response.MemberProfileResponse;
import com.meetple.backend.domain.meeting.entity.ParticipationStatus;
import com.meetple.backend.domain.meeting.entity.MeetingStatus;
import com.meetple.backend.domain.meeting.repository.MeetingBookmarkRepository;
import com.meetple.backend.domain.meeting.repository.MeetingParticipationRepository;
import com.meetple.backend.domain.meeting.repository.MeetingRepository;
import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.entity.MemberRole;
import com.meetple.backend.domain.member.repository.MemberRepository;
import com.meetple.backend.global.exception.NotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private MeetingParticipationRepository participationRepository;

    @Mock
    private MeetingBookmarkRepository bookmarkRepository;

    @Mock
    private ImageService imageService;

    @Mock
    private ImageDeletionService imageDeletionService;

    @InjectMocks
    private MemberService memberService;

    @Test
    void getMyProfileReturnsCurrentMemberProfile() {
        Member member = Member.createUser("user@meetple.com", "encoded-password", "tester", "Seoul");
        ReflectionTestUtils.setField(member, "id", 1L);
        ReflectionTestUtils.setField(member, "profileImageObjectKey", "images/profile/1/profile.png");
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(imageService.createFileUrl("images/profile/1/profile.png"))
                .willReturn("https://example.com/profile.png");
        given(meetingRepository.countByHostId(1L)).willReturn(3L);
        given(participationRepository.countByMemberIdAndStatusAndMeetingStatusIn(
                1L,
                ParticipationStatus.APPROVED,
                java.util.List.of(MeetingStatus.RECRUITING, MeetingStatus.FULL)
        ))
                .willReturn(4L);
        given(bookmarkRepository.countByMemberId(1L)).willReturn(5L);

        MemberProfileResponse response = memberService.getMyProfile(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("user@meetple.com");
        assertThat(response.nickname()).isEqualTo("tester");
        assertThat(response.introduction()).isNull();
        assertThat(response.profileImageUrl()).isEqualTo("https://example.com/profile.png");
        assertThat(response.region()).isEqualTo("Seoul");
        assertThat(response.role()).isEqualTo(MemberRole.USER);
        assertThat(response.createdMeetingsCount()).isEqualTo(3);
        assertThat(response.joinedMeetingsCount()).isEqualTo(4);
        assertThat(response.likedMeetingsCount()).isEqualTo(5);
    }

    @Test
    void getMyProfileRejectsUnknownMember() {
        given(memberRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.getMyProfile(1L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("회원을 찾을 수 없습니다.");
    }

    @Test
    void updateMyProfileImageUpdatesMemberAndReturnsProfile() {
        Member member = Member.createUser("user@meetple.com", "encoded-password", "tester", null);
        ReflectionTestUtils.setField(member, "id", 1L);
        ReflectionTestUtils.setField(member, "profileImageObjectKey", "images/profile/1/old.png");
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
        given(imageService.resolveOwnedObjectKey(
                1L,
                ImageUploadPurpose.PROFILE,
                "images/profile/1/avatar.png"
        )).willReturn("images/profile/1/avatar.png");
        given(imageService.createFileUrl("images/profile/1/avatar.png"))
                .willReturn("https://cdn.meetple.com/images/profile/1/avatar.png");

        MemberProfileResponse response = memberService.updateMyProfileImage(
                1L,
                "images/profile/1/avatar.png"
        );

        assertThat(member.getProfileImageObjectKey()).isEqualTo("images/profile/1/avatar.png");
        assertThat(response.profileImageUrl())
                .isEqualTo("https://cdn.meetple.com/images/profile/1/avatar.png");
        verify(memberRepository).findByIdForUpdate(1L);
        verify(imageDeletionService).schedule("images/profile/1/old.png");
    }

    @Test
    void deleteMyProfileImageClearsMemberImage() {
        Member member = Member.createUser("user@meetple.com", "encoded-password", "tester", null);
        ReflectionTestUtils.setField(member, "id", 1L);
        ReflectionTestUtils.setField(member, "profileImageObjectKey", "images/profile/1/avatar.png");
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));

        MemberProfileResponse response = memberService.deleteMyProfileImage(1L);

        assertThat(member.getProfileImageObjectKey()).isNull();
        assertThat(response.profileImageUrl()).isNull();
        verify(memberRepository).findByIdForUpdate(1L);
        verify(imageDeletionService).schedule("images/profile/1/avatar.png");
    }

    @Test
    void updateMyProfileUpdatesNicknameAndIntroduction() {
        Member member = Member.createUser("user@meetple.com", "encoded-password", "tester", null);
        ReflectionTestUtils.setField(member, "id", 1L);
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));

        MemberProfileResponse response = memberService.updateMyProfile(
                1L,
                " 모임친구 ",
                "같이 산책해요"
        );

        assertThat(member.getNickname()).isEqualTo("모임친구");
        assertThat(member.getIntroduction()).isEqualTo("같이 산책해요");
        assertThat(response.nickname()).isEqualTo("모임친구");
        assertThat(response.introduction()).isEqualTo("같이 산책해요");
        verify(memberRepository).findByIdForUpdate(1L);
    }

}
