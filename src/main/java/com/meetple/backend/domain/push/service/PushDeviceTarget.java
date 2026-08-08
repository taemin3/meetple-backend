package com.meetple.backend.domain.push.service;

public record PushDeviceTarget(
        Long deviceTokenId,
        String token
) {
}
