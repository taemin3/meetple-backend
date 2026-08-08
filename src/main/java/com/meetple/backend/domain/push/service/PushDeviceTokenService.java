package com.meetple.backend.domain.push.service;

import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.repository.MemberRepository;
import com.meetple.backend.domain.push.dto.request.RegisterPushDeviceTokenRequest;
import com.meetple.backend.domain.push.entity.PushDeviceToken;
import com.meetple.backend.domain.push.fcm.InvalidPushTarget;
import com.meetple.backend.domain.push.repository.PushDeviceTokenCleanupRepository;
import com.meetple.backend.domain.push.repository.PushDeviceTokenRepository;
import com.meetple.backend.global.exception.NotFoundException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class PushDeviceTokenService {

    private static final int MAX_REGISTRATION_ATTEMPTS = 3;

    private final PushDeviceTokenRepository pushDeviceTokenRepository;
    private final PushDeviceTokenCleanupRepository pushDeviceTokenCleanupRepository;
    private final MemberRepository memberRepository;
    private final TransactionTemplate transactionTemplate;

    public void register(Long memberId, RegisterPushDeviceTokenRequest request) {
        DataIntegrityViolationException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_REGISTRATION_ATTEMPTS; attempt++) {
            try {
                transactionTemplate.executeWithoutResult(status ->
                        registerInTransaction(memberId, request)
                );
                return;
            } catch (DataIntegrityViolationException exception) {
                lastFailure = exception;
            }
        }
        throw lastFailure;
    }

    private void registerInTransaction(Long memberId, RegisterPushDeviceTokenRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("Member not found."));

        Optional<PushDeviceToken> deviceMatch =
                pushDeviceTokenRepository.findByDeviceIdForUpdate(request.deviceId());
        String tokenHash = PushTokenHash.sha256(request.token());
        Optional<PushDeviceToken> tokenMatch =
                pushDeviceTokenRepository.findByTokenHashForUpdate(tokenHash);

        PushDeviceToken target = selectRegistrationTarget(deviceMatch, tokenMatch);
        if (target == null) {
            target = PushDeviceToken.create(
                    member,
                    request.deviceId(),
                    request.token(),
                    tokenHash,
                    request.platform()
            );
        } else {
            target.refresh(member, request.deviceId(), request.token(), tokenHash, request.platform());
        }
        pushDeviceTokenRepository.saveAndFlush(target);
    }

    @Transactional
    public void removeDevice(Long memberId, String deviceId) {
        pushDeviceTokenRepository.deleteByMemberIdAndDeviceId(memberId, deviceId);
    }

    @Transactional
    public void removeAllDevices(Long memberId) {
        pushDeviceTokenRepository.deleteAllByMemberId(memberId);
    }

    @Transactional(readOnly = true)
    public List<PushDeviceTarget> findTargets(Collection<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return List.of();
        }
        return pushDeviceTokenRepository.findAllByMemberIdIn(memberIds).stream()
                .map(token -> new PushDeviceTarget(
                        token.getId(),
                        token.getToken(),
                        token.getTokenHash()
                ))
                .toList();
    }

    @Transactional
    public void removeInvalidTargets(Collection<InvalidPushTarget> invalidTargets) {
        pushDeviceTokenCleanupRepository.deleteAllMatching(invalidTargets);
    }

    private PushDeviceToken selectRegistrationTarget(
            Optional<PushDeviceToken> deviceMatch,
            Optional<PushDeviceToken> tokenMatch
    ) {
        if (deviceMatch.isPresent() && tokenMatch.isPresent()
                && !deviceMatch.get().getId().equals(tokenMatch.get().getId())) {
            pushDeviceTokenRepository.delete(tokenMatch.get());
            pushDeviceTokenRepository.flush();
            return deviceMatch.get();
        }
        return deviceMatch.orElseGet(() -> tokenMatch.orElse(null));
    }
}
