package com.thriftbite.backend.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private boolean approved;

    /** Created by the public demo (see DemoData). */
    @Column(nullable = false)
    private boolean demo;

    /** Goes up when the password changes; login tokens from an older version are refused. */
    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    /** Set when a buyer deletes their account. The row stays, without the username. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected User() {
        // required by JPA
    }

    public User(String username, String passwordHash, Role role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        // Consumers can use the app right away; owners wait for an admin.
        this.approved = role != Role.OWNER;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void approve() {
        this.approved = true;
    }

    public void markDemo() {
        this.demo = true;
    }

    public void changeUsername(String newUsername) {
        this.username = newUsername;
    }

    public void changePasswordHash(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    /** A new password also logs out every device that's still holding an old login token. */
    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.tokenVersion++;
    }

    /**
     * Deletes the account while keeping the row, so the stores' past reservations still add up:
     * the username is replaced, the password can never match again, and every login token dies.
     */
    public void scrub(String placeholderUsername, String unusablePasswordHash, Instant now) {
        this.username = placeholderUsername;
        this.passwordHash = unusablePasswordHash;
        this.approved = false;
        this.tokenVersion++;
        this.deletedAt = now;
    }

    /** What stores see on a past reservation: the username, or "Deleted account". */
    public String getDisplayName() {
        return deletedAt == null ? username : "Deleted account";
    }

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public boolean isApproved() { return approved; }
    public boolean isDemo() { return demo; }
    public int getTokenVersion() { return tokenVersion; }
    public boolean isDeleted() { return deletedAt != null; }
    public Instant getCreatedAt() { return createdAt; }
}
