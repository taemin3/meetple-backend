package com.meetple.backend.domain.push.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.repository.MemberRepository;
import com.meetple.backend.domain.push.dto.request.RegisterPushDeviceTokenRequest;
import com.meetple.backend.domain.push.entity.PushDevicePlatform;
import com.meetple.backend.domain.push.entity.PushDeviceToken;
import com.meetple.backend.domain.push.fcm.InvalidPushTarget;
import com.meetple.backend.domain.push.repository.PushDeviceTokenCleanupRepository;
import com.meetple.backend.domain.push.repository.PushDeviceTokenRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class PushDeviceTokenServiceTest {

    @Mock
    private PushDeviceTokenRepository pushDeviceTokenRepository;

    @Mock
    private PushDeviceTokenCleanupRepository pushDeviceTokenCleanupRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private PushDeviceTokenService pushDeviceTokenService;

    @org.junit.jupiter.api.BeforeEach
    void setUpTransactionTemplate() {
        org.mockito.Mockito.lenient().doAnswer(invocation -> {
            java.util.function.Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(org.mockito.Mockito.mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    @Test
    void registersNewInstallationForMember() {
        Member member = member(1L);
        RegisterPushDeviceTokenRequest request = request("device-1", "token-1");
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(pushDeviceTokenRepository.findByDeviceIdForUpdate("device-1")).willReturn(Optional.empty());
        given(pushDeviceTokenRepository.findByTokenHashForUpdate(PushTokenHash.sha256("token-1")))
                .willReturn(Optional.empty());

        pushDeviceTokenService.register(1L, request);

        ArgumentCaptor<PushDeviceToken> captor = ArgumentCaptor.forClass(PushDeviceToken.class);
        verify(pushDeviceTokenRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getMember()).isSameAs(member);
        assertThat(captor.getValue().getDeviceId()).isEqualTo("device-1");
        assertThat(captor.getValue().getToken()).isEqualTo("token-1");
    }

    @Test
    void mergesDeviceAndTokenRowsWhenBothAlreadyExist() {
        Member oldMember = member(1L);
        Member newMember = member(2L);
        PushDeviceToken deviceMatch = token(10L, oldMember, "device-1", "old-token");
        PushDeviceToken tokenMatch = token(11L, oldMember, "old-device", "token-1");
        RegisterPushDeviceTokenRequest request = request("device-1", "token-1");
        given(memberRepository.findById(2L)).willReturn(Optional.of(newMember));
        given(pushDeviceTokenRepository.findByDeviceIdForUpdate("device-1"))
                .willReturn(Optional.of(deviceMatch));
        given(pushDeviceTokenRepository.findByTokenHashForUpdate(PushTokenHash.sha256("token-1")))
                .willReturn(Optional.of(tokenMatch));

        pushDeviceTokenService.register(2L, request);

        verify(pushDeviceTokenRepository).delete(tokenMatch);
        verify(pushDeviceTokenRepository).flush();
        verify(pushDeviceTokenRepository).saveAndFlush(deviceMatch);
        assertThat(deviceMatch.getMember()).isSameAs(newMember);
        assertThat(deviceMatch.getToken()).isEqualTo("token-1");
    }

    @Test
    void returnsAllDevicesForRecipients() {
        Member member = member(1L);
        given(pushDeviceTokenRepository.findAllByMemberIdIn(List.of(1L)))
                .willReturn(List.of(
                        token(10L, member, "device-1", "token-1"),
                        token(11L, member, "device-2", "token-2")
                ));

        List<PushDeviceTarget> targets = pushDeviceTokenService.findTargets(List.of(1L));

        assertThat(targets).containsExactly(
                new PushDeviceTarget(10L, "token-1"),
                new PushDeviceTarget(11L, "token-2")
        );
    }

    @Test
    void retriesConcurrentInsertConflictInANewTransaction() {
        Member member = member(1L);
        RegisterPushDeviceTokenRequest request = request("device-1", "token-1");
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(pushDeviceTokenRepository.findByDeviceIdForUpdate("device-1"))
                .willReturn(Optional.empty());
        given(pushDeviceTokenRepository.findByTokenHashForUpdate(PushTokenHash.sha256("token-1")))
                .willReturn(Optional.empty());
        given(pushDeviceTokenRepository.saveAndFlush(any(PushDeviceToken.class)))
                .willThrow(new DataIntegrityViolationException("concurrent unique conflict"))
                .willAnswer(invocation -> invocation.getArgument(0));

        pushDeviceTokenService.register(1L, request);

        verify(transactionTemplate, times(2)).executeWithoutResult(any());
        verify(pushDeviceTokenRepository, times(2)).saveAndFlush(any(PushDeviceToken.class));
    }

    @Test
    void removesInvalidTargetsInBatch() {
        List<InvalidPushTarget> invalidTargets = List.of(
                new InvalidPushTarget(10L, "hash-10"),
                new InvalidPushTarget(11L, "hash-11")
        );

        pushDeviceTokenService.removeInvalidTargets(invalidTargets);

        verify(pushDeviceTokenCleanupRepository).deleteAllMatching(invalidTargets);
    }

    private RegisterPushDeviceTokenRequest request(String deviceId, String token) {
        return new RegisterPushDeviceTokenRequest(deviceId, token, PushDevicePlatform.ANDROID);
    }

    private Member member(Long id) {
        Member member = Member.createUser("user" + id + "@meetple.com", "password", "user" + id, null);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private PushDeviceToken token(
            Long id,
            Member member,
            String deviceId,
            String token
    ) {
        PushDeviceToken deviceToken = PushDeviceToken.create(
                member,
                deviceId,
                token,
                PushTokenHash.sha256(token),
                PushDevicePlatform.ANDROID
        );
        ReflectionTestUtils.setField(deviceToken, "id", id);
        return deviceToken;
    }
}
