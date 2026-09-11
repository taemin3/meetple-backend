package com.meetple.backend.domain.member.repository;

import com.meetple.backend.domain.member.entity.Member;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {

    @Query("select m from Member m where m.email = :email and m.deletedAt is null")
    Optional<Member> findByEmail(@Param("email") String email);

    @Query("select (count(m) > 0) from Member m where m.email = :email and m.deletedAt is null")
    boolean existsByEmail(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.id = :memberId and m.deletedAt is null")
    Optional<Member> findByIdForUpdate(@Param("memberId") Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.id = :memberId")
    Optional<Member> findAnyByIdForUpdate(@Param("memberId") Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.email = :email and m.deletedAt is null")
    Optional<Member> findByEmailForUpdate(@Param("email") String email);
}
