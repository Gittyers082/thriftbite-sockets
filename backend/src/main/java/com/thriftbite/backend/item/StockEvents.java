package com.thriftbite.backend.item;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Writes the stock history (item_events). Every method must run inside the
 * transaction that changes the stock, so the history can never disagree with
 * the real numbers: if the change rolls back, so does its event.
 *
 * Plain SQL instead of JPA on purpose: each event copies the item's store and
 * its stock *after* the change straight from the items row, in one statement.
 */
@Component
public class StockEvents {

    private final JdbcTemplate jdbc;

    public StockEvents(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Records a change that has already been written to {@code items}. Flush
     * any pending JPA changes to the item first, so qty_after reads the new value.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID itemId, StockEventType type, int qtyDelta, UUID reservationId, UUID actorId) {
        jdbc.update("""
                INSERT INTO item_events (item_id, store_id, type, qty_delta, qty_after, reservation_id, actor_id)
                SELECT id, store_id, ?, ?, quantity, ?, ? FROM items WHERE id = ?
                """, type.name(), qtyDelta, reservationId, actorId, itemId);
    }

    /**
     * Takes everything past its expiry time off sale and records what was left
     * over, as ONE statement (a data-modifying CTE). There's no moment where an
     * item is expired but its EXPIRED event is missing.
     *
     * @return how many items expired
     */
    @Transactional
    public int expireOldItems(Instant now) {
        Timestamp at = Timestamp.from(now);
        return jdbc.update("""
                WITH expired AS (
                    UPDATE items
                       SET status = 'EXPIRED', version = version + 1, updated_at = ?
                     WHERE status IN ('AVAILABLE', 'SOLD_OUT') AND expires_at <= ?
                 RETURNING id, store_id, quantity
                )
                INSERT INTO item_events (item_id, store_id, type, qty_delta, qty_after, occurred_at)
                SELECT id, store_id, 'EXPIRED', 0, quantity, ? FROM expired
                """, at, at, at);
    }
}
