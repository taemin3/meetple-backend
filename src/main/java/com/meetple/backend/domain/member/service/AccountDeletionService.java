package com.meetple.backend.domain.member.service;

import com.meetple.backend.domain.auth.config.AccountDeletionProperties;
import com.meetple.backend.domain.auth.config.EmailVerificationProperties;
import com.meetple.backend.domain.auth.dto.request.AccountDeletionCompleteRequest;
import com.meetple.backend.domain.auth.dto.request.EmailVerificationConfirmRequest;
import com.meetple.backend.domain.auth.dto.request.EmailVerificationSendRequest;
import com.meetple.backend.domain.auth.dto.response.AccountDeletionVerificationResponse;
import com.meetple.backend.domain.auth.email.EmailDeliveryPurpose;
import com.meetple.backend.domain.auth.email.EmailDeliveryService;
import com.meetple.backend.domain.auth.repository.AccountDeletionRepository;
import com.meetple.backend.domain.auth.repository.RefreshTokenRepository;
import com.meetple.backend.domain.auth.service.EmailAddressNormalizer;
import com.meetple.backend.domain.auth.service.EmailVerificationHasher;
import com.meetple.backend.domain.auth.service.EmailVerificationSecretGenerator;
import com.meetple.backend.domain.chat.repository.ChatMessageRepository;
import com.meetple.backend.domain.chat.repository.ChatNotificationSettingRepository;
import com.meetple.backend.domain.chat.repository.ChatReadStateRepository;
import com.meetple.backend.domain.image.service.ImageDeletionService;
import com.meetple.backend.domain.meeting.entity.Meeting;
import com.meetple.backend.domain.meeting.entity.MeetingParticipation;
import com.meetple.backend.domain.meeting.entity.MeetingStatus;
import com.meetple.backend.domain.meeting.entity.ParticipationStatus;
import com.meetple.backend.domain.meeting.repository.MeetingBookmarkRepository;
import com.meetple.backend.domain.meeting.repository.MeetingParticipationRepository;
import com.meetple.backend.domain.meeting.repository.MeetingRepository;
import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.repository.MemberRepository;
import com.meetple.backend.domain.notification.repository.NotificationRepository;
import com.meetple.backend.domain.notification.service.NotificationService;
import com.meetple.backend.domain.push.service.PushDeviceTokenService;
import com.meetple.backend.global.exception.BadRequestException;
import com.meetple.backend.global.exception.BaseException;
import com.meetple.backend.global.exception.ConflictException;
import com.meetple.backend.global.exception.NotFoundException;
import com.meetple.backend.global.response.ErrorStatus;
import com.meetple.backend.global.websocket.ChatSessionInvalidationEvent;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountDeletionService {

    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;
    private static final String MEMBER_NOT_FOUND = "회원을 찾을 수 없습니다.";
    private static final String DELETED_MESSAGE = "[탈퇴한 회원의 메시지]";
    private static final String HOST_WITHDRAWAL_REASON = "주최자 회원 탈퇴로 취소되었습니다.";
    private static final List<MeetingStatus> ACTIVE_MEETING_STATUSES = List.of(
            MeetingStatus.RECRUITING,
            MeetingStatus.FULL
    );

    private final MemberRepository memberRepository;
    private final MeetingRepository meetingRepository;
    private final MeetingParticipationRepository participationRepository;
    private final MeetingBookmarkRepository bookmarkRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatNotificationSettingRepository chatNotificationSettingRepository;
    private final ChatReadStateRepository chatReadStateRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final PushDeviceTokenService pushDeviceTokenService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ImageDeletionService imageDeletionService;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final AccountDeletionRepository accountDeletionRepository;
    private final EmailDeliveryService emailDeliveryService;
    private final EmailVerificationSecretGenerator secretGenerator;
    private final EmailVerificationHasher hasher;
    private final EmailVerificationProperties emailProperties;
    private final AccountDeletionProperties deletionProperties;

    @Transactional
    public void deleteAuthenticated(Long memberId, String currentPassword) {
        Member member = memberRepository.findAnyByIdForUpdate(memberId)
                .orElseThrow(() -> new NotFoundException(MEMBER_NOT_FOUND));
        if (member.isDeleted()) {
            throw new ConflictException(ErrorStatus.ACCOUNT_ALREADY_DELETED);
        }
        if (!StringUtils.hasText(currentPassword)
                || currentPassword.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES
                || !passwordEncoder.matches(currentPassword, member.getPassword())) {
            throw new BadRequestException(ErrorStatus.ACCOUNT_DELETION_PASSWORD_INVALID);
        }
        deleteMember(member);
    }

    @Transactional
    public void sendVerificationCode(
            EmailVerificationSendRequest request,
            String requesterIdentifier
    ) {
        String email = EmailAddressNormalizer.normalize(request.email());
        if (!accountDeletionRepository.acquireSendPermit(
                requesterIdentifier,
                emailProperties.requesterRateLimitWindow(),
                emailProperties.requesterRateLimit(),
                emailProperties.globalRateLimitWindow(),
                emailProperties.globalRateLimit()
        )) {
            throw new BaseException(ErrorStatus.EMAIL_VERIFICATION_RATE_LIMITED);
        }

        String code = secretGenerator.generateCode();
        String codeHash = hasher.hashCode(email, code);
        Optional<Member> targetMember = memberRepository.findByEmail(email);
        if (!accountDeletionRepository.saveChallengeIfAllowed(
                email,
                codeHash,
                targetMember.map(Member::getId).orElse(0L),
                emailProperties.codeTtl(),
                emailProperties.resendCooldown()
        )) {
            throw new BaseException(ErrorStatus.EMAIL_VERIFICATION_SEND_TOO_SOON);
        }

        emailDeliveryService.schedule(
                EmailDeliveryPurpose.ACCOUNT_DELETION,
                email,
                code,
                codeHash,
                targetMember.isPresent(),
                emailProperties.codeTtl()
        );
    }

    public AccountDeletionVerificationResponse confirm(
            EmailVerificationConfirmRequest request,
            String requesterIdentifier
    ) {
        String email = EmailAddressNormalizer.normalize(request.email());
        if (!accountDeletionRepository.acquireConfirmPermit(
                requesterIdentifier,
                emailProperties.confirmationRequesterRateLimitWindow(),
                emailProperties.confirmationRequesterRateLimit()
        )) {
            throw new BaseException(ErrorStatus.EMAIL_VERIFICATION_CONFIRM_RATE_LIMITED);
        }

        String token = secretGenerator.generateToken();
        AccountDeletionRepository.CodeVerificationResult result = accountDeletionRepository
                .verifyCodeAndSaveToken(
                        email,
                        hasher.hashCode(email, request.code()),
                        emailProperties.maxAttempts(),
                        token,
                        deletionProperties.tokenTtl(),
                        requesterIdentifier
                );
        validateVerificationResult(result);
        return new AccountDeletionVerificationResponse(
                token,
                deletionProperties.tokenTtl().toSeconds()
        );
    }

    @Transactional
    public void deleteWithVerifiedEmail(AccountDeletionCompleteRequest request) {
        String email = EmailAddressNormalizer.normalize(request.email());
        Instant claimStartedAt = Instant.now();
        AccountDeletionRepository.ClaimedToken claimedToken = accountDeletionRepository
                .claimToken(request.accountDeletionToken(), email)
                .orElseThrow(() -> new BadRequestException(
                        ErrorStatus.ACCOUNT_DELETION_TOKEN_INVALID
                ));
        Instant tokenExpiresAt = claimStartedAt.plus(claimedToken.remainingTtl());
        boolean transactionSynchronizationActive =
                TransactionSynchronizationManager.isSynchronizationActive();
        if (transactionSynchronizationActive) {
            registerRollbackRestore(request, email, claimedToken.memberId(), tokenExpiresAt);
        }

        try {
            Member member = memberRepository.findByIdForUpdate(claimedToken.memberId())
                    .filter(candidate -> candidate.getEmail().equals(email))
                    .orElseThrow(() -> new BadRequestException(
                            ErrorStatus.ACCOUNT_DELETION_TOKEN_INVALID
                    ));
            if (!Instant.now().isBefore(tokenExpiresAt)) {
                throw new BadRequestException(ErrorStatus.ACCOUNT_DELETION_TOKEN_INVALID);
            }
            deleteMember(member);
        } catch (RuntimeException exception) {
            if (!transactionSynchronizationActive) {
                restoreClaimQuietly(
                        request.accountDeletionToken(),
                        email,
                        claimedToken.memberId(),
                        tokenExpiresAt
                );
            }
            throw exception;
        }
    }

    private void deleteMember(Member member) {
        Long memberId = member.getId();
        String profileImageObjectKey = member.getProfileImageObjectKey();

        List<Meeting> hostedMeetings = meetingRepository.findByHostIdAndStatusIn(
                memberId,
                ACTIVE_MEETING_STATUSES
        );
        hostedMeetings.forEach(meeting -> meeting.cancel(HOST_WITHDRAWAL_REASON));
        hostedMeetings.forEach(meeting -> eventPublisher.publishEvent(
                ChatSessionInvalidationEvent.meetingCanceled(meeting.getId())
        ));
        hostedMeetings.forEach(meeting -> participationRepository
                .findByMeetingIdAndStatus(meeting.getId(), ParticipationStatus.APPROVED)
                .forEach(participation -> notificationService.notify(
                        participation.getMember(),
                        "MEETING_CANCELED",
                        "모임 취소",
                        cancellationNotificationMessage(meeting.getTitle()),
                        meeting.getId()
                )));

        for (MeetingParticipation participation
                : participationRepository.findAllByMemberIdForUpdate(memberId)) {
            if (participation.getStatus() == ParticipationStatus.APPROVED
                    && !participation.getMeeting().isClosed()) {
                participation.getMeeting().decreaseCurrentPeople();
            }
            if (participation.getStatus() == ParticipationStatus.PENDING
                    || participation.getStatus() == ParticipationStatus.APPROVED) {
                participation.cancel();
            }
            participation.erasePersonalMessage();
        }

        bookmarkRepository.deleteAllByMemberId(memberId);
        chatNotificationSettingRepository.deleteAllByMemberId(memberId);
        chatReadStateRepository.deleteAllByMemberId(memberId);
        notificationRepository.anonymizeParticipationActorByNickname(member.getNickname());
        notificationRepository.deleteAllByMemberId(memberId);
        chatMessageRepository.anonymizeAllBySenderId(memberId, DELETED_MESSAGE);
        pushDeviceTokenService.removeAllDevices(memberId);

        if (StringUtils.hasText(profileImageObjectKey)) {
            imageDeletionService.schedule(profileImageObjectKey);
        }
        member.anonymize(
                "deleted-" + memberId + "-" + UUID.randomUUID() + "@deleted.invalid",
                passwordEncoder.encode(UUID.randomUUID().toString())
        );

        refreshTokenRepository.deleteAllByMemberId(memberId);
        eventPublisher.publishEvent(ChatSessionInvalidationEvent.member(memberId));
    }

    private void registerRollbackRestore(
            AccountDeletionCompleteRequest request,
            String email,
            Long memberId,
            Instant tokenExpiresAt
    ) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    restoreClaimQuietly(
                            request.accountDeletionToken(),
                            email,
                            memberId,
                            tokenExpiresAt
                    );
                }
            }
        });
    }

    private void restoreClaimQuietly(
            String token,
            String email,
            Long memberId,
            Instant tokenExpiresAt
    ) {
        Duration remainingTtl = Duration.between(Instant.now(), tokenExpiresAt);
        if (remainingTtl.isZero() || remainingTtl.isNegative()) {
            return;
        }
        try {
            accountDeletionRepository.restoreTokenIfNoNewerToken(
                    token,
                    email,
                    memberId,
                    remainingTtl
            );
        } catch (RuntimeException restoreException) {
            log.warn("Failed to restore account deletion token after rollback", restoreException);
        }
    }

    private String cancellationNotificationMessage(String meetingTitle) {
        String message = meetingTitle + " 모임이 취소되었습니다. 사유: " + HOST_WITHDRAWAL_REASON;
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private void validateVerificationResult(
            AccountDeletionRepository.CodeVerificationResult result
    ) {
        switch (result) {
            case VERIFIED -> {
                return;
            }
            case INVALID -> throw new BadRequestException(
                    ErrorStatus.EMAIL_VERIFICATION_CODE_INVALID
            );
            case EXPIRED -> throw new BadRequestException(
                    ErrorStatus.EMAIL_VERIFICATION_CODE_EXPIRED
            );
            case ATTEMPTS_EXCEEDED -> throw new BaseException(
                    ErrorStatus.EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED
            );
        }
    }
}
