package com.thriftbite.backend.cart;

import com.thriftbite.backend.item.Item;
import com.thriftbite.backend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "cart_items")
public class CartItem {

    @EmbeddedId
    private CartItemId id;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @MapsId("itemId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id")
    private Item item;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    protected CartItem() {
        // required by JPA
    }

    public CartItem(User user, Item item, int quantity) {
        this.id = new CartItemId(user.getId(), item.getId());
        this.user = user;
        this.item = item;
        this.quantity = quantity;
    }

    @PrePersist
    void onCreate() {
        addedAt = Instant.now();
    }

    public void changeQuantity(int newQuantity) {
        this.quantity = newQuantity;
    }

    public CartItemId getId() { return id; }
    public User getUser() { return user; }
    public Item getItem() { return item; }
    public int getQuantity() { return quantity; }
    public Instant getAddedAt() { return addedAt; }
}
