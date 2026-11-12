package com.thriftbite.backend.item;

import com.thriftbite.backend.item.dto.UsualListing;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Works out a store's "usual listings" from its stock history: each food it
 * has listed in the last five weeks, with how the most relevant day went.
 *
 * Bakeries sell differently on a Sunday than on a Tuesday, so for each food
 * the same weekday as the day being posted for wins; if it was never listed
 * on that weekday, the most recent day is used.
 */
@Component
public class UsualListings {

    static final ZoneId ZONE = ZoneId.of("Asia/Manila");

    private final NamedParameterJdbcTemplate jdbc;

    UsualListings(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<UsualListing> forStore(UUID storeId, LocalDate postingFor) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("store", storeId)
                .addValue("tz", ZONE.getId())
                .addValue("day", postingFor)
                .addValue("weekday", postingFor.getDayOfWeek().getValue());   // 1 = Monday, as ISODOW

        Set<String> alreadyListed = new HashSet<>(jdbc.queryForList("""
                SELECT lower(name) FROM items
                 WHERE store_id = :store AND status IN ('AVAILABLE', 'SOLD_OUT') AND expires_at > now()
                   AND (pickup_start AT TIME ZONE :tz)::date = CAST(:day AS date)
                """, params, String.class));

        return jdbc.query("""
                WITH past AS (
                    SELECT i.name, i.description, i.price, i.original_price, i.pickup_location, i.pickup_start,
                           (h.listed_at AT TIME ZONE :tz)::date AS day,
                           h.listed,
                           h.sold_out_at,
                           COALESCE((SELECT SUM(ri.quantity)
                                       FROM reservation_items ri JOIN reservations r ON r.id = ri.reservation_id
                                      WHERE ri.item_id = i.id AND r.status = 'COMPLETED'), 0) AS sold
                      FROM items i
                      JOIN LATERAL (
                          SELECT MIN(e.occurred_at) FILTER (WHERE e.type = 'LISTED')                           AS listed_at,
                                 COALESCE(SUM(e.qty_delta) FILTER (WHERE e.type IN ('LISTED', 'ADJUSTED')), 0) AS listed,
                                 MIN(e.occurred_at) FILTER (WHERE e.type = 'SOLD_OUT')                         AS sold_out_at
                            FROM item_events e
                           WHERE e.item_id = i.id
                      ) h ON h.listed_at IS NOT NULL
                     WHERE i.store_id = :store
                       AND i.expires_at <= now()                          -- that day is over
                       AND i.expires_at > now() - interval '35 days'
                ), chosen AS (
                    SELECT DISTINCT ON (lower(name)) *, (EXTRACT(ISODOW FROM day) = :weekday) AS same_weekday
                      FROM past
                     ORDER BY lower(name), (EXTRACT(ISODOW FROM day) = :weekday) DESC, day DESC, pickup_start DESC
                )
                SELECT * FROM chosen ORDER BY lower(name)
                """, params, (rs, row) -> {
                    int listed = rs.getInt("listed");
                    int sold = rs.getInt("sold");
                    Timestamp soldOut = rs.getTimestamp("sold_out_at");
                    Instant soldOutAt = soldOut == null ? null : soldOut.toInstant();
                    Instant pickupStart = rs.getTimestamp("pickup_start").toInstant();
                    return new UsualListing(
                            rs.getString("name"), rs.getString("description"),
                            rs.getBigDecimal("price"), rs.getBigDecimal("original_price"),
                            rs.getString("pickup_location"),
                            rs.getObject("day", LocalDate.class), rs.getBoolean("same_weekday"),
                            listed, sold,
                            soldOutAt == null ? null : LocalTime.ofInstant(soldOutAt, ZONE).withNano(0),
                            suggest(listed, sold, soldOutAt, pickupStart),
                            alreadyListed.contains(rs.getString("name").toLowerCase()));
                });
    }

    /**
     * A nudge, not a forecast. If it was all reserved before pickup even opened, people wanted
     * more: try about a fifth more. If 60% or less was picked up, try what was actually picked up.
     */
    static Integer suggest(int listed, int sold, Instant soldOutAt, Instant pickupStart) {
        if (listed <= 0) {
            return null;
        }
        if (soldOutAt != null && soldOutAt.isBefore(pickupStart)) {
            return listed + Math.max(1, Math.round(listed * 0.2f));
        }
        if (sold * 10 <= listed * 6) {
            return Math.max(1, sold);
        }
        return null;
    }
}
