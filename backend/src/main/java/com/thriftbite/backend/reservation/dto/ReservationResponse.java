package com.thriftbite.backend.reservation.dto;

import com.thriftbite.backend.reservation.Reservation;
import com.thriftbite.backend.reservation.ReservationItem;
import com.thriftbite.backend.reservation.ReservationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param claimCode only included for the buyer. The owner never sees it: the buyer
 *                  shows it at pickup, which proves the right person is collecting.
 */
public record ReservationResponse(
        UUID id,
        ReservationStatus status,
        String claimCode,
        Instant pickupDeadline,
        BigDecimal total,
        String buyerName,
        String ownerName,
        Instant createdAt,
        Instant completedAt,
        List<Line> items
) {

    public record Line(UUID itemId, String name, BigDecimal unitPrice, int quantity, String pickupLocation) {

        static Line from(ReservationItem line) {
            return new Line(line.getItem().getId(), line.getItemName(), line.getUnitPrice(),
                    line.getQuantity(), line.getItem().getPickupLocation());
        }
    }

    public static ReservationResponse forBuyer(Reservation r) {
        return build(r, r.getClaimCode());
    }

    public static ReservationResponse forOwner(Reservation r) {
        return build(r, null);
    }

    private static ReservationResponse build(Reservation r, String claimCode) {
        return new ReservationResponse(
                r.getId(), r.getStatus(), claimCode, r.getPickupDeadline(), r.getTotal(),
                r.getBuyer().getUsername(), r.getOwner().getUsername(),
                r.getCreatedAt(), r.getCompletedAt(),
                r.getItems().stream().map(Line::from).toList());
    }
}
