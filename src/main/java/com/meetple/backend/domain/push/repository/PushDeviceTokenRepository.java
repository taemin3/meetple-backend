package com.meetple.backend.domain.push.repository;

import com.meetple.backend.domain.push.entity.PushDeviceToken;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PushDeviceTokenRepository extends JpaRepository<PushDeviceToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from PushDeviceToken token where token.deviceId = :deviceId")
    Optional<PushDeviceToken> findByDeviceIdForUpdate(@Param("deviceId") String deviceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from PushDeviceToken token where token.tokenHash = :tokenHash")
    Optional<PushDeviceToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    List<PushDeviceToken> findAllByMemberIdIn(Collection<Long> memberIds);

    long deleteByMemberIdAndDeviceId(Long memberId, String deviceId);

    long deleteAllByMemberId(Long memberId);
}
