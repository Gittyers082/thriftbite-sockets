package com.thriftbite.backend.reservation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    List<Reservation> findByBuyerIdOrderByCreatedAtDesc(UUID buyerId);

    List<Reservation> findByOwnerIdAndStatusOrderByPickupDeadlineAsc(UUID ownerId, ReservationStatus status);
}
