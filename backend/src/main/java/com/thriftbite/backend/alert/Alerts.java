package com.thriftbite.backend.alert;

import com.thriftbite.backend.live.LiveChange.AlertsChanged;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Writes buyers' alerts. Every method runs inside the transaction that makes the
 * change it reports, so there's never an alert for something that was rolled back.
 * The live hint to the buyer's open screens goes out after that transaction commits.
 *
 * Plain SQL on purpose: "everyone who follows this store" is one statement,
 * however many followers there are.
 */
@Component
public class Alerts {

    private static final String ZONE = "Asia/Manila";
    private static final DateTimeFormatter CLOCK =
            DateTimeFormatter.ofPattern("h:mm a", Locale.US).withZone(ZoneId.of(ZONE));
    /** An alert names this many listings, then says "and 3 more". */
    private static final int NAMES_SHOWN = 3;

    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher live;

    Alerts(JdbcTemplate jdbc, ApplicationEventPublisher live) {
        this.jdbc = jdbc;
        this.live = live;
    }

    /**
     * A store posted food. Each follower with alerts on gets one alert per store per day;
     * if the store posts again the same day, that alert is rewritten and becomes unread again.
     * Flush the new listing to the database before calling this.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void dealsPosted(UUID storeId) {
        jdbc.query("""
                SELECT s.name, s.slug, COUNT(*) AS deals, MIN(i.price) AS from_price,
                       string_agg(i.name, '|' ORDER BY i.created_at, i.name) AS names
                  FROM items i JOIN stores s ON s.id = i.store_id
                 WHERE i.store_id = ? AND i.status = 'AVAILABLE' AND i.expires_at > now()
                   AND (i.created_at AT TIME ZONE ?)::date = (now() AT TIME ZONE ?)::date
                 GROUP BY s.name, s.slug
                """, rs -> {
                    int deals = rs.getInt("deals");
                    String title = rs.getString("name") + (deals == 1 ? " posted a deal" : " posted " + deals + " deals");
                    String body = list(Arrays.asList(rs.getString("names").split("\\|"))) + ", from "
                            + peso(rs.getBigDecimal("from_price")) + ".";
                    List<UUID> told = jdbc.queryForList("""
                            INSERT INTO alerts (user_id, kind, store_id, day, title, body, link)
                            SELECT f.user_id, 'DEAL', f.store_id, (now() AT TIME ZONE ?)::date, ?, ?, ?
                              FROM store_follows f JOIN users u ON u.id = f.user_id
                             WHERE f.store_id = ? AND f.alerts_on AND u.deleted_at IS NULL
                            ON CONFLICT (user_id, store_id, day) WHERE kind = 'DEAL'
                            DO UPDATE SET title = EXCLUDED.title, body = EXCLUDED.body, created_at = now(), read_at = NULL
                            RETURNING user_id
                            """, UUID.class, ZONE, cut(title, 200), cut(body, 500), "/stores/" + rs.getString("slug"), storeId);
                    told.forEach(userId -> live.publishEvent(new AlertsChanged(userId)));
                }, storeId, ZONE, ZONE);
    }

    /** The buyer just reserved: a reminder of where and by when, linked to the claim-code ticket. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserved(UUID reservationId) {
        jdbc.query("""
                SELECT r.buyer_id, r.store_id, r.pickup_deadline, r.total, s.name
                  FROM reservations r JOIN stores s ON s.id = r.store_id WHERE r.id = ?
                """, rs -> {
                    Instant deadline = rs.getTimestamp("pickup_deadline").toInstant();
                    insert(rs.getObject("buyer_id", UUID.class), "PICKUP", rs.getObject("store_id", UUID.class), reservationId,
                            "Pick up at " + rs.getString("name") + " by " + CLOCK.format(deadline),
                            "Show your claim code at the counter and pay " + peso(rs.getBigDecimal("total")) + ".",
                            "/pickup/" + reservationId);
                }, reservationId);
    }

    /** The store handed the food over: the reminder goes, and the buyer sees what they saved. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void pickedUp(UUID reservationId) {
        clearReminder(reservationId, false);
        jdbc.query("""
                SELECT r.buyer_id, r.store_id, s.name,
                       SUM((ri.original_unit_price - ri.unit_price) * ri.quantity) AS saved,
                       string_agg(ri.item_name, '|' ORDER BY ri.item_name) AS names
                  FROM reservations r
                  JOIN stores s ON s.id = r.store_id
                  JOIN reservation_items ri ON ri.reservation_id = r.id
                 WHERE r.id = ?
                 GROUP BY r.buyer_id, r.store_id, s.name
                """, rs -> {
                    BigDecimal saved = rs.getBigDecimal("saved");
                    String food = list(Arrays.asList(rs.getString("names").split("\\|")));
                    insert(rs.getObject("buyer_id", UUID.class), "PICKED_UP", rs.getObject("store_id", UUID.class), reservationId,
                            "Picked up at " + rs.getString("name"),
                            saved.signum() > 0 ? "You saved " + peso(saved) + " on " + food + "." : "You picked up " + food + ".",
                            "/reservations");
                }, reservationId);
    }

    /** The pickup time passed. Said plainly, because it also shows on the buyer's pickup record. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void missed(UUID reservationId) {
        clearReminder(reservationId, false);
        jdbc.query("""
                SELECT r.buyer_id, r.store_id, s.name
                  FROM reservations r JOIN stores s ON s.id = r.store_id WHERE r.id = ?
                """, rs -> {
                    insert(rs.getObject("buyer_id", UUID.class), "MISSED", rs.getObject("store_id", UUID.class), reservationId,
                            "You missed your pickup at " + rs.getString("name"),
                            "The pickup time passed, so the food went back on sale. Missed pickups show on your account page.",
                            "/me");
                }, reservationId);
    }

    /** An admin answered the buyer's problem report. Nothing for a buyer who has since deleted their account. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reportAnswered(UUID reportId) {
        jdbc.query("""
                SELECT r.reporter_id, r.store_id, r.reply, s.name
                  FROM reports r JOIN stores s ON s.id = r.store_id JOIN users u ON u.id = r.reporter_id
                 WHERE r.id = ? AND u.deleted_at IS NULL
                """, rs -> {
                    insert(rs.getObject("reporter_id", UUID.class), "REPORT", rs.getObject("store_id", UUID.class), null,
                            "We answered your report about " + rs.getString("name"), rs.getString("reply"), "/me");
                }, reportId);
    }

    /** The buyer cancelled: nothing to tell them, but the reminder shouldn't linger. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelled(UUID reservationId) {
        clearReminder(reservationId, true);
    }

    /** Old alerts are noise, and they are personal data that's no longer needed. */
    @Transactional
    public int deleteOlderThan(Instant cutoff) {
        return jdbc.update("DELETE FROM alerts WHERE created_at < ?", java.sql.Timestamp.from(cutoff));
    }

    private void insert(UUID userId, String kind, UUID storeId, UUID reservationId, String title, String body, String link) {
        jdbc.update("""
                INSERT INTO alerts (user_id, kind, store_id, reservation_id, title, body, link)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, userId, kind, storeId, reservationId, cut(title, 200), cut(body, 500), link);
        live.publishEvent(new AlertsChanged(userId));
    }

    /** @param tell false when another alert follows straight away, which tells the buyer's screens itself */
    private void clearReminder(UUID reservationId, boolean tell) {
        List<UUID> buyers = jdbc.queryForList(
                "DELETE FROM alerts WHERE reservation_id = ? AND kind = 'PICKUP' RETURNING user_id", UUID.class, reservationId);
        if (tell) {
            buyers.forEach(userId -> live.publishEvent(new AlertsChanged(userId)));
        }
    }

    /**
     * "A", "A and B", "A, B and C", "A, B, C and 2 more". Listing names often have commas of
     * their own ("Pandesal, bag of 10"); then the names are kept apart with semicolons instead.
     */
    static String list(List<String> names) {
        if (names.size() <= 1) {
            return names.isEmpty() ? "" : names.get(0);
        }
        List<String> shown = names.subList(0, Math.min(names.size(), NAMES_SHOWN));
        String more = names.size() > NAMES_SHOWN ? (names.size() - NAMES_SHOWN) + " more" : null;
        if (names.stream().anyMatch(name -> name.contains(","))) {
            return String.join("; ", shown) + (more == null ? "" : "; and " + more);
        }
        if (more != null) {
            return String.join(", ", shown) + " and " + more;
        }
        return String.join(", ", shown.subList(0, shown.size() - 1)) + " and " + shown.get(shown.size() - 1);
    }

    static String peso(BigDecimal amount) {
        return "₱" + String.format(Locale.US, "%,.2f", amount);
    }

    private static String cut(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
