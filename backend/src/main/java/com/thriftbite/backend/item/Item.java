package com.thriftbite.backend.item;

import com.thriftbite.backend.store.Store;
import com.thriftbite.backend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "items")
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    /** Who put the listing up (the column keeps its v1 name). The store, not this person, owns it. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User listedBy;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "original_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal originalPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "pickup_location", nullable = false, length = 200)
    private String pickupLocation;

    @Column(name = "pickup_start", nullable = false)
    private Instant pickupStart;

    @Column(name = "pickup_end", nullable = false)
    private Instant pickupEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ItemStatus status;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Item() {
        // required by JPA
    }

    public Item(Store store, User listedBy, String name, String description,
                BigDecimal price, BigDecimal originalPrice, int quantity,
                Instant expiresAt, String pickupLocation,
                Instant pickupStart, Instant pickupEnd) {
        this.store = store;
        this.listedBy = listedBy;
        this.name = name;
        this.description = description;
        this.price = price;
        this.originalPrice = originalPrice;
        this.quantity = quantity;
        this.expiresAt = expiresAt;
        this.pickupLocation = pickupLocation;
        this.pickupStart = pickupStart;
        this.pickupEnd = pickupEnd;
        this.status = quantity > 0 ? ItemStatus.AVAILABLE : ItemStatus.SOLD_OUT;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Owner edits a listing. Stock decides whether it's for sale. */
    public void update(String name, String description,
                       BigDecimal price, BigDecimal originalPrice, int quantity,
                       Instant expiresAt, String pickupLocation,
                       Instant pickupStart, Instant pickupEnd) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.originalPrice = originalPrice;
        this.quantity = quantity;
        this.expiresAt = expiresAt;
        this.pickupLocation = pickupLocation;
        this.pickupStart = pickupStart;
        this.pickupEnd = pickupEnd;
        this.status = quantity > 0 ? ItemStatus.AVAILABLE : ItemStatus.SOLD_OUT;
    }

    /**
     * Soft delete: the row stays because past reservations still point at it,
     * but the item disappears from every listing.
     */
    public void remove() {
        this.status = ItemStatus.REMOVED;
    }

    public boolean isRemoved() {
        return status == ItemStatus.REMOVED;
    }

    public boolean belongsTo(UUID storeId) {
        return store.getId().equals(storeId);
    }

    public UUID getId() { return id; }
    public Store getStore() { return store; }
    public User getListedBy() { return listedBy; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getOriginalPrice() { return originalPrice; }
    public int getQuantity() { return quantity; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getPickupLocation() { return pickupLocation; }
    public Instant getPickupStart() { return pickupStart; }
    public Instant getPickupEnd() { return pickupEnd; }
    public ItemStatus getStatus() { return status; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
