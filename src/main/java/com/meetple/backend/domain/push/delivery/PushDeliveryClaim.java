package com.meetple.backend.domain.push.delivery;

import com.meetple.backend.domain.push.service.PushDeviceTarget;
import java.util.List;
import java.util.UUID;

public record PushDeliveryClaim(
        UUID claimId,
        List<PushDeviceTarget> targets,
        boolean blockedByActiveClaim
) {

    public static PushDeliveryClaim empty() {
        return new PushDeliveryClaim(null, List.of(), false);
    }

    public static PushDeliveryClaim blocked() {
        return new PushDeliveryClaim(null, List.of(), true);
    }
}
