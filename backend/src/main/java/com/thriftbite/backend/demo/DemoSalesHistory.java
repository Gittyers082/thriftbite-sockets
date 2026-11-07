package com.thriftbite.backend.demo;

import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Thirty days of believable past sales for one demo store, so the Sales
 * screen has history to show. Written straight to the tables in batches
 * (Render's free CPU is small), in exactly the shape the app writes them:
 * items, reservations, reservation lines and the matching stock events.
 *
 * Same seed every time, so the demo shows the same story after each reset:
 * cheese rolls sell out early, monay is slow, Tuesdays leave the most behind.
 */
final class DemoSalesHistory {

    private record Product(String name, String price, String usual, int listed, double pull, double early) {
    }

    private static final List<Product> PRODUCTS = List.of(
            new Product("Pandesal, bag of 10", "25.00", "50.00", 6, 0.92, 0),
            new Product("Ube cheese ensaymada, box of 4", "90.00", "150.00", 3, 0.85, 0),
            new Product("Spanish bread, 6 pcs", "40.00", "70.00", 4, 0.70, 0),
            new Product("Cheese rolls, 6 pcs", "45.00", "90.00", 2, 1.25, 1.1),
            new Product("Monay, bag of 6", "20.00", "40.00", 4, 0.55, 0));

    private static final Map<DayOfWeek, Double> DEMAND = new EnumMap<>(Map.of(
            DayOfWeek.MONDAY, 0.72, DayOfWeek.TUESDAY, 0.55, DayOfWeek.WEDNESDAY, 0.70, DayOfWeek.THURSDAY, 0.76,
            DayOfWeek.FRIDAY, 0.90, DayOfWeek.SATURDAY, 0.96, DayOfWeek.SUNDAY, 1.00));

    private record Event(UUID itemId, String type, int delta, UUID reservationId, UUID actorId, Instant at) {
    }

    private final JdbcTemplate jdbc;
    private final ZoneId zone;
    private final Random random = new Random(7);

    private final List<Object[]> items = new ArrayList<>();
    private final List<Object[]> reservations = new ArrayList<>();
    private final List<Object[]> lines = new ArrayList<>();
    private final List<Event> events = new ArrayList<>();

    DemoSalesHistory(JdbcTemplate jdbc, ZoneId zone) {
        this.jdbc = jdbc;
        this.zone = zone;
    }

    void seed(UUID storeId, UUID ownerId, List<UUID> buyerIds, LocalDate today, int days, String pickupLocation) {
        for (int ago = days; ago >= 1; ago--) {
            seedDay(storeId, ownerId, buyerIds, today.minusDays(ago), pickupLocation);
        }
        write(storeId);
    }

    private void seedDay(UUID storeId, UUID ownerId, List<UUID> buyerIds, LocalDate day, String where) {
        Instant listedAt = at(day, 15, 30);
        Instant pickupStart = at(day, 17, 0);
        Instant pickupEnd = at(day, 19, 0);
        Instant expiresAt = at(day, 21, 0);
        double demand = DEMAND.get(day.getDayOfWeek());
        boolean missedToday = random.nextDouble() < 0.22;

        for (Product product : PRODUCTS) {
            UUID itemId = UUID.randomUUID();
            double want = product.listed() * demand * product.pull() * (0.8 + random.nextDouble() * 0.4);
            int sold = (int) Math.max(0, Math.min(product.listed(), Math.round(want)));
            boolean soldOut = sold == product.listed();
            // Popular food sells out earlier; cheese rolls earliest of all.
            double sellOutHour = 19 - Math.min(3.2, (want - product.listed() + 0.6) * 3)
                    - random.nextDouble() * 0.4 - product.early();
            Instant lastReservationBy = soldOut ? atHour(day, Math.max(15.75, sellOutHour)) : at(day, 18, 40);

            int left = product.listed();
            events.add(new Event(itemId, "LISTED", product.listed(), null, ownerId, listedAt));

            // what was handed over, 1 or 2 units per buyer
            List<Instant> reservedAt = new ArrayList<>();
            int remaining = sold;
            while (remaining > 0) {
                int qty = Math.min(remaining, random.nextDouble() < 0.4 ? 2 : 1);
                remaining -= qty;
                Instant created = between(at(day, 15, 35), lastReservationBy);
                Instant completed = max(created.plusSeconds(15 * 60), between(pickupStart, pickupEnd.minusSeconds(60)));
                if (completed.isAfter(pickupEnd)) {
                    completed = pickupEnd.minusSeconds(60);
                }
                UUID reservationId = reservation(buyerIds, storeId, "COMPLETED", pickupEnd, created, completed);
                line(reservationId, itemId, product, qty);
                events.add(new Event(itemId, "RESERVED", -qty, reservationId, null, created));
                reservedAt.add(created);
            }
            if (soldOut && !reservedAt.isEmpty()) {
                Instant last = reservedAt.stream().max(Comparator.naturalOrder()).orElseThrow();
                events.add(new Event(itemId, "SOLD_OUT", 0, null, null, last.plusMillis(1)));
            }
            left -= sold;

            // now and then a buyer reserves one and never comes; it goes back on sale at 7 PM, unsold
            if (missedToday && left > 0) {
                missedToday = false;
                Instant created = between(at(day, 15, 40), at(day, 17, 30));
                UUID reservationId = reservation(buyerIds, storeId, "EXPIRED", pickupEnd, created, null);
                line(reservationId, itemId, product, 1);
                events.add(new Event(itemId, "RESERVED", -1, reservationId, null, created));
                events.add(new Event(itemId, "RELEASED", 1, reservationId, null, pickupEnd.plusSeconds(120)));
            }

            events.add(new Event(itemId, "EXPIRED", 0, null, null, expiresAt));
            items.add(new Object[]{itemId, storeId, ownerId, product.name(), null, new BigDecimal(product.price()),
                    new BigDecimal(product.usual()), left, ts(expiresAt), where, ts(pickupStart), ts(pickupEnd),
                    "EXPIRED", ts(listedAt), ts(expiresAt)});
        }
    }

    private UUID reservation(List<UUID> buyerIds, UUID storeId, String status, Instant deadline,
                             Instant created, Instant completed) {
        UUID id = UUID.randomUUID();
        UUID buyer = buyerIds.get(random.nextInt(buyerIds.size()));
        String code = String.format("%06d", random.nextInt(1_000_000));
        Instant expired = "EXPIRED".equals(status) ? deadline.plusSeconds(120) : null;
        // total is filled in after the lines are added
        reservations.add(new Object[]{id, buyer, storeId, status, code, ts(deadline), BigDecimal.ZERO,
                ts(created), ts(completed), ts(expired)});
        return id;
    }

    private void line(UUID reservationId, UUID itemId, Product product, int qty) {
        lines.add(new Object[]{UUID.randomUUID(), reservationId, itemId, product.name(),
                new BigDecimal(product.price()), new BigDecimal(product.usual()), qty});
        for (Object[] r : reservations) {
            if (r[0].equals(reservationId)) {
                r[6] = ((BigDecimal) r[6]).add(new BigDecimal(product.price()).multiply(BigDecimal.valueOf(qty)));
            }
        }
    }

    private void write(UUID storeId) {
        jdbc.batchUpdate("""
                INSERT INTO items (id, store_id, owner_id, name, description, price, original_price, quantity,
                                   expires_at, pickup_location, pickup_start, pickup_end, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, items);
        jdbc.batchUpdate("""
                INSERT INTO reservations (id, buyer_id, store_id, status, claim_code, pickup_deadline, total,
                                          created_at, completed_at, expired_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, reservations);
        jdbc.batchUpdate("""
                INSERT INTO reservation_items (id, reservation_id, item_id, item_name, unit_price, original_unit_price, quantity)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, lines);

        // Stock after each event, per item, in time order: the same numbers the app would have recorded.
        events.sort(Comparator.comparing(Event::itemId).thenComparing(Event::at));
        List<Object[]> rows = new ArrayList<>();
        UUID current = null;
        int stock = 0;
        for (Event e : events) {
            if (!e.itemId().equals(current)) {
                current = e.itemId();
                stock = 0;
            }
            stock += e.delta();
            rows.add(new Object[]{e.itemId(), storeId, e.type(), e.delta(), stock, e.reservationId(), e.actorId(),
                    ts(e.at())});
        }
        jdbc.batchUpdate("""
                INSERT INTO item_events (item_id, store_id, type, qty_delta, qty_after, reservation_id, actor_id, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, rows);
    }

    private Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant();
    }

    private Instant atHour(LocalDate day, double hour) {
        int minutes = (int) Math.round(hour * 60);
        return day.atStartOfDay(zone).plusMinutes(minutes).toInstant();
    }

    private Instant between(Instant from, Instant to) {
        long span = Math.max(1, to.getEpochSecond() - from.getEpochSecond());
        return from.plusSeconds((long) (random.nextDouble() * span));
    }

    private static Instant max(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
