package com.meetple.backend.domain.push.repository;

import com.meetple.backend.domain.push.fcm.InvalidPushTarget;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PushDeviceTokenCleanupRepository {

    private static final int DELETE_BATCH_SIZE = 500;

    private final JdbcTemplate jdbcTemplate;

    public void deleteAllMatching(Collection<InvalidPushTarget> invalidTargets) {
        if (invalidTargets.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(
                "DELETE FROM push_device_tokens WHERE id = ? AND token_hash = ?",
                invalidTargets,
                DELETE_BATCH_SIZE,
                (statement, target) -> {
                    statement.setLong(1, target.targetId());
                    statement.setString(2, target.tokenHash());
                }
        );
    }
}
