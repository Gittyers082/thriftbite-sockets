package com.thriftbite.backend.reservation;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    /** Loads everything a response needs in one go, instead of one query per line. */
    @EntityGraph(attributePaths = {"buyer", "owner", "items", "items.item"})
    List<Reservation> findByBuyerIdOrderByCreatedAtDesc(UUID buyerId);

    @EntityGraph(attributePaths = {"buyer", "owner", "items", "items.item"})
    List<Reservation> findByOwnerIdAndStatusOrderByPickupDeadlineAsc(UUID ownerId, ReservationStatus status);

    @EntityGraph(attributePaths = {"buyer", "owner", "items", "items.item"})
    Optional<Reservation> findWithDetailsById(UUID id);
}
