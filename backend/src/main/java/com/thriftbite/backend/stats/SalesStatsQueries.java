package com.thriftbite.backend.stats;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * The SQL behind the Sales screen. Each number is computed in Postgres
 * (grouping, FILTER, generate_series, percentile_cont) rather than by loading
 * rows into Java and looping.
 *
 * Every timestamp is turned into a Manila calendar day with
 * {@code AT TIME ZONE :tz} before grouping, so a pickup at 7:30 AM Manila
 * (11:30 PM UTC the day before) lands on the right day.
 */
@Repository
class SalesStatsQueries {

    /**
     * Food listed in the period, one row per item: how much was put up, sold,
     * left over, and when it sold out. Shared by the item and weekday queries.
     */
    private static final String LISTED_IN_PERIOD = """
            WITH cohort AS (
                SELECT e.item_id,
                       MIN(e.occurred_at) FILTER (WHERE e.type = 'LISTED')                        AS listed_at,
                       COALESCE(SUM(e.qty_delta) FILTER (WHERE e.type IN ('LISTED', 'ADJUSTED')), 0) AS listed,
                       MIN(e.occurred_at) FILTER (WHERE e.type = 'SOLD_OUT')                      AS sold_out_at,
                       MAX(e.qty_after) FILTER (WHERE e.type IN ('EXPIRED', 'REMOVED'))            AS leftover
                  FROM item_events e
                 WHERE e.store_id = :store AND e.occurred_at >= :start
                 GROUP BY e.item_id
                HAVING MIN(e.occurred_at) FILTER (WHERE e.type = 'LISTED') >= :start
                   AND MIN(e.occurred_at) FILTER (WHERE e.type = 'LISTED') <  :end
            ),
            sold AS (
                SELECT ri.item_id, SUM(ri.quantity) AS units, SUM(ri.unit_price * ri.quantity) AS money
                  FROM reservation_items ri
                  JOIN reservations r ON r.id = ri.reservation_id
                 WHERE r.store_id = :store AND r.status = 'COMPLETED'
                   AND ri.item_id IN (SELECT item_id FROM cohort)
                 GROUP BY ri.item_id
            ),
            per_item AS (
                SELECT i.name,
                       (c.listed_at AT TIME ZONE :tz)::date AS day,
                       c.listed,
                       COALESCE(s.units, 0) AS sold,
                       COALESCE(s.money, 0) AS money,
                       c.leftover,
                       c.sold_out_at
                  FROM cohort c
                  JOIN items i ON i.id = c.item_id
                  LEFT JOIN sold s ON s.item_id = c.item_id
            )
            """;

    private final NamedParameterJdbcTemplate jdbc;

    SalesStatsQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    record DayRow(LocalDate date, BigDecimal money, int units, int pickups, BigDecimal discount) {
    }

    record Outcomes(int completed, int missed, int cancelled, BigDecimal missedValue) {
    }

    record ItemRow(String name, int listed, int sold, int leftover, BigDecimal money,
                   int daysListed, int daysSoldOut, LocalTime usualSellOutTime) {
    }

    record WeekdayRow(DayOfWeek weekday, int days, int leftover) {
    }

    record HourRow(int hour, int pickups) {
    }

    /** One row per calendar day in the period, including days with no sales. */
    List<DayRow> days(Period p) {
        return jdbc.query("""
                WITH days AS (
                    SELECT d::date AS day
                      FROM generate_series(CAST(:from AS date), CAST(:to AS date), interval '1 day') AS d
                ),
                sold AS (
                    SELECT (r.completed_at AT TIME ZONE :tz)::date                     AS day,
                           SUM(ri.unit_price * ri.quantity)                            AS money,
                           SUM(ri.quantity)                                            AS units,
                           COUNT(DISTINCT r.id)                                        AS pickups,
                           SUM((ri.original_unit_price - ri.unit_price) * ri.quantity) AS discount
                      FROM reservations r
                      JOIN reservation_items ri ON ri.reservation_id = r.id
                     WHERE r.store_id = :store AND r.status = 'COMPLETED'
                       AND r.completed_at >= :start AND r.completed_at < :end
                     GROUP BY 1
                )
                SELECT d.day,
                       COALESCE(s.money, 0)    AS money,
                       COALESCE(s.units, 0)    AS units,
                       COALESCE(s.pickups, 0)  AS pickups,
                       COALESCE(s.discount, 0) AS discount
                  FROM days d
                  LEFT JOIN sold s ON s.day = d.day
                 ORDER BY d.day
                """, p.params(), (rs, i) -> new DayRow(rs.getObject("day", LocalDate.class),
                rs.getBigDecimal("money"), rs.getInt("units"), rs.getInt("pickups"), rs.getBigDecimal("discount")));
    }

    /** Money made in the period, for the "up 12% on the 7 days before" comparison. */
    BigDecimal moneyMade(Period p) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(ri.unit_price * ri.quantity), 0)
                  FROM reservations r
                  JOIN reservation_items ri ON ri.reservation_id = r.id
                 WHERE r.store_id = :store AND r.status = 'COMPLETED'
                   AND r.completed_at >= :start AND r.completed_at < :end
                """, p.params(), BigDecimal.class);
    }

    /** How the reservations made in the period ended. */
    Outcomes outcomes(Period p) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FILTER (WHERE status = 'COMPLETED')            AS completed,
                       COUNT(*) FILTER (WHERE status = 'EXPIRED')              AS missed,
                       COUNT(*) FILTER (WHERE status = 'CANCELLED')            AS cancelled,
                       COALESCE(SUM(total) FILTER (WHERE status = 'EXPIRED'), 0) AS missed_value
                  FROM reservations
                 WHERE store_id = :store AND created_at >= :start AND created_at < :end
                """, p.params(), (rs, i) -> new Outcomes(rs.getInt("completed"), rs.getInt("missed"),
                rs.getInt("cancelled"), rs.getBigDecimal("missed_value")));
    }

    /** Each food across the days it was listed, best seller (by share sold) first. */
    List<ItemRow> items(Period p) {
        return jdbc.query(LISTED_IN_PERIOD + """
                SELECT MIN(name)                  AS name,
                       SUM(listed)                AS listed,
                       SUM(sold)                  AS sold,
                       SUM(COALESCE(leftover, 0)) AS leftover,
                       SUM(money)                 AS money,
                       COUNT(*)                   AS days_listed,
                       COUNT(sold_out_at)         AS days_sold_out,
                       percentile_cont(0.5) WITHIN GROUP (
                           ORDER BY EXTRACT(EPOCH FROM (sold_out_at AT TIME ZONE :tz)::time)
                       )                          AS median_sell_out_seconds
                  FROM per_item
                 GROUP BY lower(name)
                 ORDER BY SUM(sold)::numeric / NULLIF(SUM(listed), 0) DESC NULLS LAST, MIN(name)
                """, p.params(), (rs, i) -> new ItemRow(rs.getString("name"), rs.getInt("listed"),
                rs.getInt("sold"), rs.getInt("leftover"), rs.getBigDecimal("money"),
                rs.getInt("days_listed"), rs.getInt("days_sold_out"), secondsToTime(rs)));
    }

    /** Food left over by the weekday it was listed, with how many of each weekday the period has. */
    List<WeekdayRow> leftoverByWeekday(Period p) {
        return jdbc.query(LISTED_IN_PERIOD + """
                , weekdays AS (
                    SELECT EXTRACT(ISODOW FROM d)::int AS dow, COUNT(*) AS days
                      FROM generate_series(CAST(:from AS date), CAST(:to AS date), interval '1 day') AS d
                     GROUP BY 1
                )
                SELECT w.dow, w.days, COALESCE(SUM(p.leftover), 0) AS leftover
                  FROM weekdays w
                  LEFT JOIN per_item p ON EXTRACT(ISODOW FROM p.day)::int = w.dow
                 GROUP BY w.dow, w.days
                 ORDER BY w.dow
                """, p.params(), (rs, i) -> new WeekdayRow(DayOfWeek.of(rs.getInt("dow")),
                rs.getInt("days"), rs.getInt("leftover")));
    }

    /** Handovers by hour of the day (Manila time); hours with none are left out. */
    List<HourRow> pickupsByHour(Period p) {
        return jdbc.query("""
                SELECT EXTRACT(HOUR FROM (completed_at AT TIME ZONE :tz))::int AS hour, COUNT(*) AS pickups
                  FROM reservations
                 WHERE store_id = :store AND status = 'COMPLETED'
                   AND completed_at >= :start AND completed_at < :end
                 GROUP BY 1
                 ORDER BY 1
                """, p.params(), (rs, i) -> new HourRow(rs.getInt("hour"), rs.getInt("pickups")));
    }

    private static LocalTime secondsToTime(ResultSet rs) throws SQLException {
        double seconds = rs.getDouble("median_sell_out_seconds");
        if (rs.wasNull()) {
            return null;
        }
        return LocalTime.ofSecondOfDay(Math.round(seconds) % 86_400).withSecond(0);
    }

    /**
     * A store and a range of calendar days, plus the instants where those days
     * start and end in the store's time zone: [start, end).
     */
    record Period(UUID storeId, LocalDate from, LocalDate to, String timeZone, Instant start, Instant end) {

        MapSqlParameterSource params() {
            return new MapSqlParameterSource()
                    .addValue("store", storeId)
                    .addValue("from", from)
                    .addValue("to", to)
                    .addValue("tz", timeZone)
                    .addValue("start", OffsetDateTime.ofInstant(start, ZoneOffset.UTC))
                    .addValue("end", OffsetDateTime.ofInstant(end, ZoneOffset.UTC));
        }
    }
}
