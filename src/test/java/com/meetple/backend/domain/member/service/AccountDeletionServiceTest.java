package com.meetple.backend.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.meetple.backend.domain.auth.config.AccountDeletionProperties;
import com.meetple.backend.domain.auth.config.EmailVerificationProperties;
import com.meetple.backend.domain.auth.dto.request.EmailVerificationSendRequest;
import com.meetple.backend.domain.auth.email.EmailDeliveryPurpose;
import com.meetple.backend.domain.auth.email.EmailDeliveryService;
import com.meetple.backend.domain.auth.repository.AccountDeletionRepository;
import com.meetple.backend.domain.auth.repository.RefreshTokenRepository;
import com.meetple.backend.domain.auth.service.EmailVerificationHasher;
import com.meetple.backend.domain.auth.service.EmailVerificationSecretGenerator;
import com.meetple.backend.domain.chat.repository.ChatMessageRepository;
import com.meetple.backend.domain.chat.repository.ChatNotificationSettingRepository;
import com.meetple.backend.domain.chat.repository.ChatReadStateRepository;
import com.meetple.backend.domain.image.service.ImageDeletionService;
import com.meetple.backend.domain.meeting.repository.MeetingBookmarkRepository;
import com.meetple.backend.domain.meeting.repository.MeetingParticipationRepository;
import com.meetple.backend.domain.meeting.repository.MeetingRepository;
import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.repository.MemberRepository;
import com.meetple.backend.domain.notification.repository.NotificationRepository;
import com.meetple.backend.domain.push.service.PushDeviceTokenService;
import com.meetple.backend.global.exception.BadRequestException;
import com.meetple.backend.global.websocket.ChatSessionInvalidationEvent;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceTest {

    @Mock private MemberRepository memberRepository;
    @Mock private MeetingRepository meetingRepository;
    @Mock private MeetingParticipationRepository participationRepository;
    @Mock private MeetingBookmarkRepository bookmarkRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private ChatNotificationSettingRepository chatNotificationSettingRepository;
    @Mock private ChatReadStateRepository chatReadStateRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private PushDeviceTokenService pushDeviceTokenService;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private ImageDeletionService imageDeletionService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private AccountDeletionRepository accountDeletionRepository;
    @Mock private EmailDeliveryService emailDeliveryService;
    @Mock private EmailVerificationSecretGenerator secretGenerator;
    @Mock private EmailVerificationHasher hasher;

    private AccountDeletionService service;

    @BeforeEach
    void setUp() {
        EmailVerificationProperties emailProperties = new EmailVerificationProperties(
                Duration.ofMinutes(5), Duration.ofMinutes(1), Duration.ofMinutes(15),
                5, Duration.ofMinutes(1), 5, Duration.ofMinutes(1), 100,
                Duration.ofMinutes(1), 10, "x".repeat(32), "noreply@example.test"
        );
        service = new AccountDeletionService(
                memberRepository, meetingRepository, participationRepository,
                bookmarkRepository, chatMessageRepository, chatNotificationSettingRepository,
                chatReadStateRepository, notificationRepository, pushDeviceTokenService,
                refreshTokenRepository, imageDeletionService, passwordEncoder, eventPublisher,
                accountDeletionRepository, emailDeliveryService, secretGenerator, hasher,
                emailProperties, new AccountDeletionProperties(Duration.ofMinutes(15))
        );
    }

    @Test
    void unknownEmailUsesSameSendFlowWithoutDeliveringMail() {
        EmailVerificationSendRequest request = new EmailVerificationSendRequest("missing@meetple.com");
        given(accountDeletionRepository.acquireSendPermit(any(), any(), any(Integer.class),
                any(), any(Integer.class))).willReturn(true);
        given(secretGenerator.generateCode()).willReturn("123456");
        given(hasher.hashCode("missing@meetple.com", "123456")).willReturn("code-hash");
        given(accountDeletionRepository.saveChallengeIfAllowed(any(), any(), any(), any()))
                .willReturn(true);
        given(memberRepository.existsByEmail("missing@meetple.com")).willReturn(false);

        service.sendVerificationCode(request, "127.0.0.1");

        verify(emailDeliveryService).schedule(
                eq(EmailDeliveryPurpose.ACCOUNT_DELETION),
                eq("missing@meetple.com"),
                eq("123456"),
                eq("code-hash"),
                eq(false),
                eq(Duration.ofMinutes(5))
        );
    }

    @Test
    void authenticatedDeletionAnonymizesAndRemovesAccountScopedData() {
        Member member = Member.createUser(
                "user@meetple.com", "encoded-password", "사용자", "서울"
        );
        ReflectionTestUtils.setField(member, "id", 7L);
        member.updateProfileImage("images/profile/7/avatar.png");
        given(memberRepository.findAnyByIdForUpdate(7L)).willReturn(Optional.of(member));
        given(passwordEncoder.matches("password123", "encoded-password")).willReturn(true);
        given(passwordEncoder.encode(any())).willReturn("unusable-password");
        given(meetingRepository.findByHostIdAndStatusIn(any(), any())).willReturn(List.of());
        given(participationRepository.findAllByMemberIdForUpdate(7L)).willReturn(List.of());

        service.deleteAuthenticated(7L, "password123");

        assertThat(member.isDeleted()).isTrue();
        assertThat(member.getEmail()).endsWith("@deleted.invalid");
        assertThat(member.getNickname()).isEqualTo("탈퇴 회원");
        assertThat(member.getIntroduction()).isNull();
        assertThat(member.getRegion()).isNull();
        verify(imageDeletionService).schedule("images/profile/7/avatar.png");
        verify(bookmarkRepository).deleteAllByMemberId(7L);
        verify(chatNotificationSettingRepository).deleteAllByMemberId(7L);
        verify(chatReadStateRepository).deleteAllByMemberId(7L);
        verify(notificationRepository).deleteAllByMemberId(7L);
        verify(chatMessageRepository).anonymizeAllBySenderId(
                7L,
                "[탈퇴한 회원의 메시지]"
        );
        verify(pushDeviceTokenService).removeAllDevices(7L);
        verify(refreshTokenRepository).deleteAllByMemberId(7L);
        verify(eventPublisher).publishEvent(any(ChatSessionInvalidationEvent.class));
    }

    @Test
    void wrongPasswordDoesNotMutateAccountData() {
        Member member = Member.createUser(
                "user@meetple.com", "encoded-password", "사용자", "서울"
        );
        ReflectionTestUtils.setField(member, "id", 7L);
        given(memberRepository.findAnyByIdForUpdate(7L)).willReturn(Optional.of(member));
        given(passwordEncoder.matches("wrong-password", "encoded-password")).willReturn(false);

        assertThatThrownBy(() -> service.deleteAuthenticated(7L, "wrong-password"))
                .isInstanceOf(BadRequestException.class);

        assertThat(member.isDeleted()).isFalse();
        verifyNoInteractions(bookmarkRepository, chatMessageRepository,
                pushDeviceTokenService, refreshTokenRepository);
    }
}
