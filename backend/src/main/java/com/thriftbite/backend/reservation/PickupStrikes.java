package com.thriftbite.backend.reservation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The missed-pickup rule. Reserving is free, so a buyer who reserves and doesn't come
 * costs a store a sale and leaves food unsold. Miss too many pickups in a short time
 * (2 in 30 days unless configured otherwise) and reserving is paused for a while (7 days),
 * counted from the latest miss.
 *
 * Only pickups the buyer simply didn't come for count: not ones they cancelled, not ones
 * cancelled because the store was suspended, and not ones an admin excused.
 */
@Service
public class PickupStrikes {

    /**
     * @param missed       pickups missed inside the window that ends now
     * @param blockedUntil when the buyer can reserve again; null if they can now
     */
    public record Standing(int missed, Instant blockedUntil) {
        public boolean blocked() {
            return blockedUntil != null;
        }
    }

    private final JdbcTemplate jdbc;
    private final int limit;
    private final Duration window;
    private final Duration block;

    PickupStrikes(JdbcTemplate jdbc,
                  @Value("${app.reservations.missed-pickups-before-block:2}") int limit,
                  @Value("${app.reservations.missed-pickup-window:P30D}") Duration window,
                  @Value("${app.reservations.missed-pickup-block:P7D}") Duration block) {
        this.jdbc = jdbc;
        this.limit = limit;
        this.window = window;
        this.block = block;
    }

    @Transactional(readOnly = true)
    public Standing of(UUID buyerId) {
        return of(buyerId, Instant.now());
    }

    Standing of(UUID buyerId, Instant now) {
        // Far enough back to see a block that started just inside the window and is still running.
        Instant since = now.minus(window).minus(block);
        List<Instant> misses = jdbc.query("""
                SELECT COALESCE(expired_at, pickup_deadline) AS missed_at
                  FROM reservations
                 WHERE buyer_id = ? AND status = 'EXPIRED' AND NOT miss_excused
                   AND COALESCE(expired_at, pickup_deadline) >= ?
                 ORDER BY missed_at DESC
                """, (rs, row) -> rs.getTimestamp("missed_at").toInstant(), buyerId, java.sql.Timestamp.from(since));
        int recent = (int) misses.stream().filter(at -> !at.isBefore(now.minus(window))).count();
        return new Standing(recent, blockedUntil(misses, now));
    }

    /**
     * A block starts at a miss that is the limit-th one within the window before it, and
     * runs for the block length. Newest first, so the first one found is the one that matters.
     */
    private Instant blockedUntil(List<Instant> newestFirst, Instant now) {
        for (int i = 0; i + limit - 1 < newestFirst.size(); i++) {
            Instant latest = newestFirst.get(i);
            Instant earliestOfRun = newestFirst.get(i + limit - 1);
            if (!earliestOfRun.isBefore(latest.minus(window))) {
                Instant until = latest.plus(block);
                return until.isAfter(now) ? until : null;
            }
        }
        return null;
    }

    public int limit() {
        return limit;
    }

    public Duration window() {
        return window;
    }

    public Duration block() {
        return block;
    }
}
