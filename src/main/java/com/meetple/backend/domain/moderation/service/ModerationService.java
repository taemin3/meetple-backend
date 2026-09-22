package com.meetple.backend.domain.moderation.service;

import com.meetple.backend.domain.chat.entity.ChatMessage;
import com.meetple.backend.domain.chat.repository.ChatMessageRepository;
import com.meetple.backend.domain.image.service.ImageService;
import com.meetple.backend.domain.meeting.entity.Meeting;
import com.meetple.backend.domain.meeting.repository.MeetingRepository;
import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.repository.MemberRepository;
import com.meetple.backend.domain.moderation.dto.request.CreateReportRequest;
import com.meetple.backend.domain.moderation.dto.response.BlockedMemberResponse;
import com.meetple.backend.domain.moderation.dto.response.ReportResponse;
import com.meetple.backend.domain.moderation.entity.MemberBlock;
import com.meetple.backend.domain.moderation.entity.Report;
import com.meetple.backend.domain.moderation.entity.ReportReason;
import com.meetple.backend.domain.moderation.repository.MemberBlockRepository;
import com.meetple.backend.domain.moderation.repository.ReportRepository;
import com.meetple.backend.global.exception.BadRequestException;
import com.meetple.backend.global.exception.NotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ModerationService {
    private static final String MEMBER_NOT_FOUND_MESSAGE = "회원을 찾을 수 없습니다.";

    private final MemberRepository memberRepository;
    private final MeetingRepository meetingRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ReportRepository reportRepository;
    private final MemberBlockRepository memberBlockRepository;
    private final ImageService imageService;

    @Transactional
    public ReportResponse createReport(Long reporterId, CreateReportRequest request) {
        Member reporter = getMember(reporterId);
        Long targetAuthorId = resolveTargetAuthorId(request);
        if (reporterId.equals(targetAuthorId)) {
            throw new BadRequestException("자신 또는 자신이 작성한 콘텐츠는 신고할 수 없습니다.");
        }
        Report report = reportRepository.save(Report.create(
                reporter, request.targetType(), request.targetId(), request.reason(),
                normalizeDescription(request.reason(), request.otherDescription())
        ));
        return ReportResponse.from(report);
    }

    @Transactional
    public void block(Long blockerId, Long blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new BadRequestException("자기 자신은 차단할 수 없습니다.");
        }
        Member blocker = memberRepository.findByIdForUpdate(blockerId)
                .orElseThrow(() -> new NotFoundException(MEMBER_NOT_FOUND_MESSAGE));
        Member blocked = getMember(blockedId);
        if (!memberBlockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId)) {
            memberBlockRepository.save(MemberBlock.create(blocker, blocked));
        }
    }

    @Transactional
    public void unblock(Long blockerId, Long blockedId) {
        getMember(blockerId);
        memberBlockRepository.deleteByBlockerIdAndBlockedId(blockerId, blockedId);
    }

    public List<BlockedMemberResponse> getBlockedMembers(Long blockerId) {
        getMember(blockerId);
        return memberBlockRepository.findByBlockerIdOrderByCreatedAtDesc(blockerId).stream()
                .map(block -> BlockedMemberResponse.from(block,
                        imageService.createFileUrl(block.getBlocked().getProfileImageObjectKey())))
                .toList();
    }

    private Long resolveTargetAuthorId(CreateReportRequest request) {
        return switch (request.targetType()) {
            case MEMBER -> getMember(request.targetId()).getId();
            case MEETING -> {
                Meeting meeting = meetingRepository.findById(request.targetId())
                        .orElseThrow(() -> new NotFoundException("모임을 찾을 수 없습니다."));
                yield meeting.getHost().getId();
            }
            case CHAT_MESSAGE -> {
                ChatMessage message = chatMessageRepository.findById(request.targetId())
                        .orElseThrow(() -> new NotFoundException("채팅 메시지를 찾을 수 없습니다."));
                yield message.getSender().getId();
            }
        };
    }

    private String normalizeDescription(ReportReason reason, String description) {
        if (reason != ReportReason.OTHER) return null;
        if (!StringUtils.hasText(description)) {
            throw new BadRequestException("기타 신고 사유를 입력해주세요.");
        }
        return description.trim();
    }

    private Member getMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException(MEMBER_NOT_FOUND_MESSAGE));
    }
}
