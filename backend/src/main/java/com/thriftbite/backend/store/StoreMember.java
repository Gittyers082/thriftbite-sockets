package com.thriftbite.backend.store;

import com.thriftbite.backend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/** Links an account to the store it works at. */
@Entity
@Table(name = "store_members")
public class StoreMember {

    @EmbeddedId
    private StoreMemberId id;

    @MapsId("storeId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id")
    private Store store;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StoreRole role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StoreMember() {
        // required by JPA
    }

    public StoreMember(Store store, User user, StoreRole role) {
        this.id = new StoreMemberId(store.getId(), user.getId());
        this.store = store;
        this.user = user;
        this.role = role;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public StoreMemberId getId() { return id; }
    public Store getStore() { return store; }
    public User getUser() { return user; }
    public StoreRole getRole() { return role; }
}
