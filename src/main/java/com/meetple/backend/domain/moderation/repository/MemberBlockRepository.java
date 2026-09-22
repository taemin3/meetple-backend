package com.meetple.backend.domain.moderation.repository;

import com.meetple.backend.domain.moderation.entity.MemberBlock;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberBlockRepository extends JpaRepository<MemberBlock, Long> {

    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    void deleteByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    @EntityGraph(attributePaths = "blocked")
    List<MemberBlock> findByBlockerIdOrderByCreatedAtDesc(Long blockerId);

    @Query("select block.blocked.id from MemberBlock block where block.blocker.id = :blockerId")
    List<Long> findBlockedMemberIds(@Param("blockerId") Long blockerId);
}
