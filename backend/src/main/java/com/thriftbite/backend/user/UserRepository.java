package com.thriftbite.backend.user;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByRole(Role role);

    List<User> findByRoleAndApprovedFalseOrderByCreatedAtAsc(Role role);

    /**
     * Loads a user and locks their row until the transaction ends (SELECT ... FOR UPDATE).
     * Checkout uses it so two checkouts by the same buyer run one after the other,
     * and both can't slip past the reservation limit at once.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> lockById(@Param("id") UUID id);
}
