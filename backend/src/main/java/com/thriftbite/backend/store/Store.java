package com.thriftbite.backend.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A bakery, carinderia or stall that lists food. People reach it through {@link StoreMember}. */
@Entity
@Table(name = "stores")
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 200)
    private String address;

    @Column(length = 200)
    private String landmark;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "gcash_accepted", nullable = false)
    private boolean gcashAccepted;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Store() {
        // required by JPA
    }

    public Store(String slug, String name) {
        this.slug = slug;
        this.name = name;
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

    /** Where to find the store. Coordinates are optional until the map pin is set. */
    public void locate(String address, String landmark, BigDecimal latitude, BigDecimal longitude) {
        this.address = address;
        this.landmark = landmark;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public void acceptGcash(boolean accepted) {
        this.gcashAccepted = accepted;
    }

    public UUID getId() { return id; }
    public String getSlug() { return slug; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public String getLandmark() { return landmark; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public boolean isGcashAccepted() { return gcashAccepted; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}
