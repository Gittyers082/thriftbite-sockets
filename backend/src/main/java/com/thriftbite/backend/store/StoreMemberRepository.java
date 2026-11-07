package com.thriftbite.backend.store;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StoreMemberRepository extends JpaRepository<StoreMember, StoreMemberId> {

    /** The store an account works at (each account has at most one). */
    @EntityGraph(attributePaths = "store")
    Optional<StoreMember> findByIdUserId(UUID userId);
}
