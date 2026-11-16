package com.thriftbite.backend.store;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface StoreMemberRepository extends JpaRepository<StoreMember, StoreMemberId> {

    /** The store an account works at (each account has at most one). */
    @EntityGraph(attributePaths = "store")
    Optional<StoreMember> findByIdUserId(UUID userId);

    /** Whether an admin has approved someone at this store. Until then the store isn't public. */
    @Query("select count(m) > 0 from StoreMember m where m.store.id = :storeId and m.user.approved = true and m.user.suspendedAt is null")
    boolean hasApprovedMember(@Param("storeId") UUID storeId);
}
