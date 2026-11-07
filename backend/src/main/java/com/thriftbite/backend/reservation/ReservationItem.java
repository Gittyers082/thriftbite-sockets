package com.thriftbite.backend.reservation;

import com.thriftbite.backend.item.Item;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "reservation_items")
public class ReservationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    // Copied at checkout so later edits to the item don't change past reservations.
    @Column(name = "item_name", nullable = false, length = 120)
    private String itemName;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    /** The usual price at checkout, so "discount given" stays right after the owner edits the item. */
    @Column(name = "original_unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal originalUnitPrice;

    @Column(nullable = false)
    private int quantity;

    protected ReservationItem() {
        // required by JPA
    }

    public ReservationItem(Item item, int quantity) {
        this.item = item;
        this.itemName = item.getName();
        this.unitPrice = item.getPrice();
        this.originalUnitPrice = item.getOriginalPrice();
        this.quantity = quantity;
    }

    void attachTo(Reservation reservation) {
        this.reservation = reservation;
    }

    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public UUID getId() { return id; }
    public Reservation getReservation() { return reservation; }
    public Item getItem() { return item; }
    public String getItemName() { return itemName; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getOriginalUnitPrice() { return originalUnitPrice; }
    public int getQuantity() { return quantity; }
}
