package com.meetple.backend.domain.member.service;

import com.meetple.backend.domain.member.dto.response.MemberProfileResponse;
import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.repository.MemberRepository;
import com.meetple.backend.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private static final String MEMBER_NOT_FOUND_MESSAGE = "회원을 찾을 수 없습니다.";

    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public MemberProfileResponse getMyProfile(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException(MEMBER_NOT_FOUND_MESSAGE));

        return MemberProfileResponse.from(member);
    }
}
