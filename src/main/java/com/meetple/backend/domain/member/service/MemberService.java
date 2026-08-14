package com.meetple.backend.domain.member.service;

import com.meetple.backend.domain.image.entity.ImageUploadPurpose;
import com.meetple.backend.domain.image.service.ImageService;
import com.meetple.backend.domain.meeting.entity.MeetingStatus;
import com.meetple.backend.domain.meeting.entity.ParticipationStatus;
import com.meetple.backend.domain.meeting.repository.MeetingBookmarkRepository;
import com.meetple.backend.domain.meeting.repository.MeetingParticipationRepository;
import com.meetple.backend.domain.meeting.repository.MeetingRepository;
import com.meetple.backend.domain.member.dto.response.MemberProfileResponse;
import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.repository.MemberRepository;
import com.meetple.backend.global.exception.NotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private static final List<MeetingStatus> ACTIVE_MEETING_STATUSES = List.of(
            MeetingStatus.RECRUITING,
            MeetingStatus.FULL
    );
    private static final String MEMBER_NOT_FOUND_MESSAGE = "회원을 찾을 수 없습니다.";

    private final MemberRepository memberRepository;
    private final MeetingRepository meetingRepository;
    private final MeetingParticipationRepository participationRepository;
    private final MeetingBookmarkRepository bookmarkRepository;
    private final ImageService imageService;

    @Transactional(readOnly = true)
    public MemberProfileResponse getMyProfile(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException(MEMBER_NOT_FOUND_MESSAGE));

        return toProfileResponse(member, memberId);
    }

    @Transactional
    public MemberProfileResponse updateMyProfileImage(Long memberId, String profileImageUrl) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException(MEMBER_NOT_FOUND_MESSAGE));

        String trustedProfileImageUrl = imageService.resolveOwnedFileUrl(
                memberId,
                ImageUploadPurpose.PROFILE,
                profileImageUrl
        );
        member.updateProfileImage(trustedProfileImageUrl);
        return toProfileResponse(member, memberId);
    }

    private MemberProfileResponse toProfileResponse(Member member, Long memberId) {
        return MemberProfileResponse.from(
                member,
                meetingRepository.countByHostId(memberId),
                participationRepository.countByMemberIdAndStatusAndMeetingStatusIn(
                        memberId,
                        ParticipationStatus.APPROVED,
                        ACTIVE_MEETING_STATUSES
                ),
                bookmarkRepository.countByMemberId(memberId)
        );
    }
}
