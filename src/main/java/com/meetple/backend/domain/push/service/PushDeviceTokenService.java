package com.meetple.backend.domain.push.service;

import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.repository.MemberRepository;
import com.meetple.backend.domain.push.dto.request.RegisterPushDeviceTokenRequest;
import com.meetple.backend.domain.push.entity.PushDeviceToken;
import com.meetple.backend.domain.push.repository.PushDeviceTokenRepository;
import com.meetple.backend.global.exception.NotFoundException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PushDeviceTokenService {

    private final PushDeviceTokenRepository pushDeviceTokenRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public void register(Long memberId, RegisterPushDeviceTokenRequest request) {
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
        pushDeviceTokenRepository.save(target);
    }

    @Transactional
    public void removeDevice(Long memberId, String deviceId) {
        pushDeviceTokenRepository.deleteByMemberIdAndDeviceId(memberId, deviceId);
    }

    @Transactional
    public void removeAllDevices(Long memberId) {
        pushDeviceTokenRepository.deleteAllByMemberId(memberId);
    }

    public List<PushDeviceTarget> findTargets(Collection<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return List.of();
        }
        return pushDeviceTokenRepository.findAllByMemberIdIn(memberIds).stream()
                .map(token -> new PushDeviceTarget(token.getId(), token.getToken()))
                .toList();
    }

    @Transactional
    public void removeInvalidTargets(Collection<Long> deviceTokenIds) {
        if (!deviceTokenIds.isEmpty()) {
            pushDeviceTokenRepository.deleteAllByIdInBatch(deviceTokenIds);
        }
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
