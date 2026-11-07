package com.thriftbite.backend.cart.dto;

import com.thriftbite.backend.cart.CartItem;
import com.thriftbite.backend.item.Item;
import com.thriftbite.backend.item.ItemStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CartResponse(List<Line> lines, BigDecimal total) {

    /**
     * @param available false if the item sold out, expired or was removed after it was
     *                  added, so the UI can warn before checkout fails
     */
    public record Line(UUID itemId, String name, BigDecimal price, int quantity, BigDecimal lineTotal,
                       int quantityLeft, boolean available, UUID storeId, String storeName,
                       String pickupLocation) {
    }

    public static CartResponse from(List<CartItem> cartItems, Instant now) {
        List<Line> lines = cartItems.stream().map(cartItem -> {
            Item item = cartItem.getItem();
            boolean available = item.getStatus() == ItemStatus.AVAILABLE
                    && item.getExpiresAt().isAfter(now)
                    && item.getQuantity() >= cartItem.getQuantity();
            return new Line(item.getId(), item.getName(), item.getPrice(), cartItem.getQuantity(),
                    item.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity())),
                    item.getQuantity(), available, item.getStore().getId(), item.getStore().getName(),
                    item.getPickupLocation());
        }).toList();
        BigDecimal total = lines.stream().map(Line::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartResponse(lines, total);
    }
}
