package com.thriftbite.backend.item.dto;

import com.thriftbite.backend.item.Item;
import com.thriftbite.backend.item.ItemStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

public record ItemResponse(
        UUID id,
        String name,
        String description,
        BigDecimal price,
        BigDecimal originalPrice,
        int discountPercent,
        int quantity,
        ItemStatus status,
        Instant expiresAt,
        String pickupLocation,
        Instant pickupStart,
        Instant pickupEnd,
        UUID ownerId,
        String ownerName,
        long version
) {

    public static ItemResponse from(Item item) {
        return new ItemResponse(
                item.getId(),
                item.getName(),
                item.getDescription(),
                item.getPrice(),
                item.getOriginalPrice(),
                discountPercent(item.getPrice(), item.getOriginalPrice()),
                item.getQuantity(),
                item.getStatus(),
                item.getExpiresAt(),
                item.getPickupLocation(),
                item.getPickupStart(),
                item.getPickupEnd(),
                item.getOwner().getId(),
                item.getOwner().getUsername(),
                item.getVersion());
    }

    /** e.g. price 25, original 50 -> 50 (% off). */
    static int discountPercent(BigDecimal price, BigDecimal originalPrice) {
        if (originalPrice.signum() <= 0) {
            return 0;
        }
        return originalPrice.subtract(price)
                .multiply(BigDecimal.valueOf(100))
                .divide(originalPrice, 0, RoundingMode.HALF_UP)
                .intValue();
    }
}
