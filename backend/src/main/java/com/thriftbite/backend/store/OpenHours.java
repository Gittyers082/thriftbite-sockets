package com.thriftbite.backend.store;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.LocalTime;
import java.util.Objects;

/** When a store opens and closes on one day, in local (Manila) time. */
@Embeddable
public class OpenHours {

    @Column(nullable = false)
    private LocalTime opens;

    @Column(nullable = false)
    private LocalTime closes;

    protected OpenHours() {
        // required by JPA
    }

    public OpenHours(LocalTime opens, LocalTime closes) {
        this.opens = opens;
        this.closes = closes;
    }

    public LocalTime getOpens() { return opens; }
    public LocalTime getCloses() { return closes; }

    // Hibernate compares these to see whether a day's hours changed.
    @Override
    public boolean equals(Object other) {
        return other instanceof OpenHours that && Objects.equals(opens, that.opens) && Objects.equals(closes, that.closes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(opens, closes);
    }
}
