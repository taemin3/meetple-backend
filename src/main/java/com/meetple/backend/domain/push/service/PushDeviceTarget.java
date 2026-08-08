package com.meetple.backend.domain.push.service;

public record PushDeviceTarget(
        Long deviceTokenId,
        String token,
        String tokenHash
) {

    public PushDeviceTarget(Long deviceTokenId, String token) {
        this(deviceTokenId, token, PushTokenHash.sha256(token));
    }
}
