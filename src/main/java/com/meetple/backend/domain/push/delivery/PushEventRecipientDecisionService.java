package com.meetple.backend.domain.push.delivery;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class PushEventRecipientDecisionService {

    private static final int MAX_DECISION_ATTEMPTS = 3;

    private final PushEventRecipientDecisionRepository decisionRepository;
    private final TransactionTemplate transactionTemplate;

    public List<Long> resolveEnabledRecipients(
            UUID eventId,
            Collection<Long> recipientMemberIds,
            Collection<Long> currentlyEnabledMemberIds
    ) {
        if (recipientMemberIds.isEmpty()) {
            return List.of();
        }

        DataIntegrityViolationException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_DECISION_ATTEMPTS; attempt++) {
            try {
                List<Long> result = transactionTemplate.execute(status -> resolveInTransaction(
                        eventId,
                        recipientMemberIds,
                        currentlyEnabledMemberIds
                ));
                return Objects.requireNonNull(result);
            } catch (DataIntegrityViolationException exception) {
                lastFailure = exception;
            }
        }
        throw lastFailure;
    }

    private List<Long> resolveInTransaction(
            UUID eventId,
            Collection<Long> recipientMemberIds,
            Collection<Long> currentlyEnabledMemberIds
    ) {
        Set<Long> uniqueRecipientIds = new LinkedHashSet<>(recipientMemberIds);
        Set<Long> currentlyEnabledIds = Set.copyOf(currentlyEnabledMemberIds);
        Map<Long, PushEventRecipientDecision> decisions = decisionRepository
                .findAllByEventIdAndMemberIdIn(eventId, uniqueRecipientIds)
                .stream()
                .collect(Collectors.toMap(
                        PushEventRecipientDecision::getMemberId,
                        Function.identity(),
                        (first, second) -> first,
                        LinkedHashMap::new
                ));

        List<PushEventRecipientDecision> newDecisions = uniqueRecipientIds.stream()
                .filter(memberId -> !decisions.containsKey(memberId))
                .map(memberId -> PushEventRecipientDecision.create(
                        eventId,
                        memberId,
                        !currentlyEnabledIds.contains(memberId)
                ))
                .toList();
        if (!newDecisions.isEmpty()) {
            decisionRepository.saveAllAndFlush(newDecisions);
            newDecisions.forEach(decision -> decisions.put(decision.getMemberId(), decision));
        }

        return uniqueRecipientIds.stream()
                .filter(memberId -> !decisions.get(memberId).isSuppressed())
                .toList();
    }
}
