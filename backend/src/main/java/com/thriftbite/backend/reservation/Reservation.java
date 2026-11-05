package com.thriftbite.backend.reservation;

import com.thriftbite.backend.user.User;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * One buyer picking up from one owner. Named "Reservation" rather than "Order"
 * because ORDER is a reserved word in SQL and JPQL.
 */
@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "claim_code", nullable = false, length = 6)
    private String claimCode;

    @Column(name = "pickup_deadline", nullable = false)
    private Instant pickupDeadline;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReservationItem> items = new ArrayList<>();

    protected Reservation() {
        // required by JPA
    }

    public Reservation(User buyer, User owner, String claimCode, Instant pickupDeadline) {
        this.buyer = buyer;
        this.owner = owner;
        this.claimCode = claimCode;
        this.pickupDeadline = pickupDeadline;
        this.status = ReservationStatus.RESERVED;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    /** Adds a line and keeps the total in sync, so the two can't drift apart. */
    public void addItem(ReservationItem item) {
        item.attachTo(this);
        items.add(item);
        total = total.add(item.lineTotal());
    }

    public void complete(Instant when) {
        this.status = ReservationStatus.COMPLETED;
        this.completedAt = when;
    }

    public void cancel() {
        this.status = ReservationStatus.CANCELLED;
    }

    public UUID getId() { return id; }
    public User getBuyer() { return buyer; }
    public User getOwner() { return owner; }
    public ReservationStatus getStatus() { return status; }
    public String getClaimCode() { return claimCode; }
    public Instant getPickupDeadline() { return pickupDeadline; }
    public BigDecimal getTotal() { return total; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public List<ReservationItem> getItems() { return Collections.unmodifiableList(items); }
}
