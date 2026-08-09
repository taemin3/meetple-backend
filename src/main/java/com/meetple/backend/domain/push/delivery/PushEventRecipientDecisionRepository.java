package com.meetple.backend.domain.push.delivery;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushEventRecipientDecisionRepository
        extends JpaRepository<PushEventRecipientDecision, Long> {

    List<PushEventRecipientDecision> findAllByEventIdAndMemberIdIn(
            UUID eventId,
            Collection<Long> memberIds
    );
}
