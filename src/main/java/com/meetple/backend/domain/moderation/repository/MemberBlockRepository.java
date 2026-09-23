package com.meetple.backend.domain.moderation.repository;

import com.meetple.backend.domain.moderation.entity.MemberBlock;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberBlockRepository extends JpaRepository<MemberBlock, Long> {

    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    void deleteByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    @Modifying
    @Query("""
            delete from MemberBlock block
            where block.blocker.id = :memberId
               or block.blocked.id = :memberId
            """)
    int deleteAllByMemberId(@Param("memberId") Long memberId);

    @EntityGraph(attributePaths = "blocked")
    Page<MemberBlock> findByBlockerId(Long blockerId, Pageable pageable);

    @Query("select block.blocked.id from MemberBlock block where block.blocker.id = :blockerId")
    List<Long> findBlockedMemberIds(@Param("blockerId") Long blockerId);

    @Query("""
            select block.blocker.id
            from MemberBlock block
            where block.blocked.id = :blockedMemberId
              and block.blocker.id in :blockerIds
            """)
    List<Long> findBlockerIdsBlockingMember(
            @Param("blockedMemberId") Long blockedMemberId,
            @Param("blockerIds") Collection<Long> blockerIds
    );
}
