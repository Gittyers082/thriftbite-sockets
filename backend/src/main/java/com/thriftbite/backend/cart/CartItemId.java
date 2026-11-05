package com.thriftbite.backend.cart;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Composite key: a user can have each item in their cart only once. */
@Embeddable
public class CartItemId implements Serializable {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    protected CartItemId() {
        // required by JPA
    }

    public CartItemId(UUID userId, UUID itemId) {
        this.userId = userId;
        this.itemId = itemId;
    }

    public UUID getUserId() { return userId; }
    public UUID getItemId() { return itemId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CartItemId other)) return false;
        return Objects.equals(userId, other.userId) && Objects.equals(itemId, other.itemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, itemId);
    }
}
