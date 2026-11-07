package com.thriftbite.backend.store;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Composite key: an account is a member of a store once. */
@Embeddable
public class StoreMemberId implements Serializable {

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    protected StoreMemberId() {
        // required by JPA
    }

    public StoreMemberId(UUID storeId, UUID userId) {
        this.storeId = storeId;
        this.userId = userId;
    }

    public UUID getStoreId() { return storeId; }
    public UUID getUserId() { return userId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StoreMemberId other)) return false;
        return Objects.equals(storeId, other.storeId) && Objects.equals(userId, other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(storeId, userId);
    }
}
