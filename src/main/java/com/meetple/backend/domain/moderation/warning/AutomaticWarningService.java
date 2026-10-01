package com.meetple.backend.domain.moderation.warning;

import static com.meetple.backend.domain.moderation.analysis.ReportAnalysisContracts.*;

import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.repository.MemberRepository;
import com.meetple.backend.domain.notification.service.NotificationService;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AutomaticWarningService {
    static final String NOTIFICATION_TYPE = "MODERATION_WARNING";
    static final String NOTIFICATION_TITLE = "운영 정책 위반 경고";
    static final String NOTIFICATION_MESSAGE =
            "회원님의 콘텐츠가 운영 정책을 위반할 수 있어 경고가 발송되었습니다. 반복 시 이용이 제한될 수 있습니다.";
    private static final Set<ModerationPriority> SAFE_PRIORITIES =
            EnumSet.of(ModerationPriority.LOW, ModerationPriority.NORMAL);

    private final AutomaticWarningProperties properties;
    private final AutomaticWarningRepository repository;
    private final MemberRepository memberRepository;
    private final NotificationService notificationService;

    public boolean issueIfEligible(long reportId, CompleteRequest request) {
        if (!isEligible(request)) {
            return false;
        }
        Optional<Long> targetMemberId = repository.insertIfAbsent(reportId);
        if (targetMemberId.isEmpty()) {
            return false;
        }
        Member target = memberRepository.findById(targetMemberId.orElseThrow())
                .orElseThrow(() -> new IllegalStateException("자동 경고 대상 회원을 찾을 수 없습니다."));
        notificationService.notify(
                target,
                NOTIFICATION_TYPE,
                NOTIFICATION_TITLE,
                NOTIFICATION_MESSAGE,
                null
        );
        return true;
    }

    private boolean isEligible(CompleteRequest request) {
        return properties.enabled()
                && request.recommendedAction() == RecommendedAction.WARNING
                && request.riskLevel() == RiskLevel.LOW
                && SAFE_PRIORITIES.contains(request.priority())
                && properties.allowedReportTypes().contains(request.reportType())
                && request.confidence().compareTo(properties.minConfidence()) >= 0;
    }
}
