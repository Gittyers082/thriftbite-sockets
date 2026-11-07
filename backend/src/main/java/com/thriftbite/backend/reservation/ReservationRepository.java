package com.thriftbite.backend.reservation;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    /** Loads everything a response needs in one go, instead of one query per line. */
    @EntityGraph(attributePaths = {"buyer", "store", "items", "items.item"})
    List<Reservation> findByBuyerIdOrderByCreatedAtDesc(UUID buyerId);

    @EntityGraph(attributePaths = {"buyer", "store", "items", "items.item"})
    List<Reservation> findByStoreIdAndStatusOrderByPickupDeadlineAsc(UUID storeId, ReservationStatus status);

    @EntityGraph(attributePaths = {"buyer", "store", "items", "items.item"})
    Optional<Reservation> findWithDetailsById(UUID id);

    /** How many reservations a buyer is holding right now (status RESERVED). */
    long countByBuyerIdAndStatus(UUID buyerId, ReservationStatus status);

    /** Active reservations whose pickup window has closed. */
    @EntityGraph(attributePaths = {"items", "items.item"})
    List<Reservation> findByStatusAndPickupDeadlineBefore(ReservationStatus status, Instant deadline);
}
