package com.thriftbite.backend.demo;

import com.thriftbite.backend.item.Item;
import com.thriftbite.backend.item.ItemRepository;
import com.thriftbite.backend.reservation.Reservation;
import com.thriftbite.backend.reservation.ReservationItem;
import com.thriftbite.backend.reservation.ReservationRepository;
import com.thriftbite.backend.user.Role;
import com.thriftbite.backend.user.User;
import com.thriftbite.backend.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

/**
 * Sample Baguio stores, food and reservations for the public demo.
 *
 * Pickup windows are set around the current time, so the demo always shows
 * food that can be reserved right now. Because of that, the data goes stale:
 * it's rebuilt on startup and whenever someone opens the demo and the data is
 * older than {@code app.demo.max-age}.
 *
 * A reset deletes every account except the bootstrap admin, so only turn this
 * on for a deployment that exists to be a demo.
 */
@Component
@ConditionalOnProperty(prefix = "app.demo", name = "enabled", havingValue = "true")
public class DemoData implements ApplicationRunner {

    public static final String BUYER = "demo.buyer";
    public static final String OWNER = "sunrise.bakery";
    public static final String ADMIN = "demo.admin";
    /** The demo buyer's code at Sunrise Bakery, the same one the designs show. */
    public static final String BUYER_CLAIM_CODE = "482913";

    private static final Logger log = LoggerFactory.getLogger(DemoData.class);
    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");

    private final UserRepository users;
    private final ItemRepository items;
    private final ReservationRepository reservations;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final String keepAdmin;
    private final Duration maxAge;

    private volatile Instant seededAt;

    public DemoData(UserRepository users, ItemRepository items, ReservationRepository reservations,
                    PasswordEncoder passwordEncoder, JdbcTemplate jdbc, PlatformTransactionManager transactions,
                    @Value("${app.admin.username:}") String bootstrapAdmin,
                    @Value("${app.demo.max-age:PT6H}") Duration maxAge) {
        this.users = users;
        this.items = items;
        this.reservations = reservations;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactions);
        this.keepAdmin = bootstrapAdmin == null ? "" : bootstrapAdmin.trim().toLowerCase(Locale.ROOT);
        this.maxAge = maxAge;
    }

    @Override
    public void run(ApplicationArguments args) {
        reset();
    }

    public Instant seededAt() {
        return seededAt;
    }

    public Instant resetsAt() {
        return seededAt == null ? null : seededAt.plus(maxAge);
    }

    /** Rebuilds the data if it's older than max-age. One reset at a time. */
    public synchronized void ensureFresh() {
        if (seededAt == null || Instant.now().isAfter(seededAt.plus(maxAge))) {
            reset();
        }
    }

    public synchronized void reset() {
        tx.executeWithoutResult(status -> {
            wipe();
            seed(Instant.now());
        });
        seededAt = Instant.now();
        log.info("Demo data reset; next reset after {}", seededAt.plus(maxAge));
    }

    private void wipe() {
        jdbc.update("DELETE FROM cart_items");
        jdbc.update("DELETE FROM reservation_items");
        jdbc.update("DELETE FROM reservations");
        jdbc.update("DELETE FROM items");
        jdbc.update("DELETE FROM users WHERE username <> ?", keepAdmin);
    }

    private void seed(Instant now) {
        // One random password nobody knows, hashed once (BCrypt is slow on purpose, and Render's
        // free CPU is small). Demo accounts are only reached through /api/demo/login.
        String unusableHash = passwordEncoder.encode(UUID.randomUUID().toString());
        User buyer = user(BUYER, Role.CONSUMER, true, unusableHash);
        User mara = user("mara.l", Role.CONSUMER, true, unusableHash);
        User jun = user("jun.d", Role.CONSUMER, true, unusableHash);
        user(ADMIN, Role.ADMIN, true, unusableHash);

        User sunrise = user(OWNER, Role.OWNER, true, unusableHash);
        User lola = user("lola.ising", Role.OWNER, true, unusableHash);
        User burnham = user("burnham.taho", Role.OWNER, true, unusableHash);
        User pinegrove = user("pinegrove.cafe", Role.OWNER, true, unusableHash);
        User kusina = user("kusina.ading", Role.OWNER, true, unusableHash);
        // waiting for the admin, so the approvals screen has something on it
        user("manang.fe.bakeshop", Role.OWNER, false, unusableHash);
        user("aurora.carinderia", Role.OWNER, false, unusableHash);
        user("hillside.cafe", Role.OWNER, false, unusableHash);

        // Windows are anchored to the top of the current hour in Manila, so every one is still open.
        Instant hour = now.atZone(MANILA).truncatedTo(ChronoUnit.HOURS).toInstant();
        Window burnhamW = new Window(hour.minus(Duration.ofMinutes(60)), hour.plus(Duration.ofMinutes(90)));
        Window sunriseW = new Window(hour.minus(Duration.ofMinutes(30)), hour.plus(Duration.ofMinutes(150)));
        Window lolaW = new Window(hour, hour.plus(Duration.ofMinutes(180)));
        Window pinegroveW = new Window(hour, hour.plus(Duration.ofMinutes(180)));
        Window kusinaW = new Window(hour.plus(Duration.ofMinutes(30)), hour.plus(Duration.ofMinutes(210)));

        String sunriseAt = "Sunrise Bakery counter, Session Road";
        Item pandesal = item(sunrise, "Pandesal, bag of 10", "Baked this morning.", "25.00", "50.00", 3, sunriseW, sunriseAt);
        Item ensaymada = item(sunrise, "Ube cheese ensaymada, box of 4", null, "90.00", "150.00", 2, sunriseW, sunriseAt);
        item(sunrise, "Spanish bread, 6 pcs", null, "40.00", "70.00", 4, sunriseW, sunriseAt);
        Item cheeseRolls = item(sunrise, "Cheese rolls, 6 pcs", null, "45.00", "90.00", 0, sunriseW, sunriseAt);

        String lolaAt = "Lola Ising's Carinderia, Bonifacio Street";
        Item adobo = item(lola, "Chicken adobo with rice", "One serving.", "60.00", "95.00", 6, lolaW, lolaAt);
        item(lola, "Pinakbet family tray", "Good for 4.", "120.00", "220.00", 2, lolaW, lolaAt);

        Item taho = item(burnham, "Strawberry taho, 2 cups", null, "20.00", "35.00", 8, burnhamW, "Burnham Park, near the lake");

        String pinegroveAt = "Pine Grove Café, Legarda Road";
        Item bananaBread = item(pinegrove, "Banana bread loaf", null, "70.00", "140.00", 3, pinegroveW, pinegroveAt);
        item(pinegrove, "Ham and cheese croissants, 2 pcs", null, "45.00", "90.00", 4, pinegroveW, pinegroveAt);

        item(kusina, "Lumpiang shanghai, 10 pcs", null, "55.00", "90.00", 5, kusinaW, "Kusina ni Ading, Abanao Street");

        // Today at Sunrise Bakery: two pickups waiting, one handed over.
        // (Stock above already has these taken out: 6 pandesal listed, 3 reserved; 3 ensaymada, 1 reserved.)
        Reservation mine = reservation(buyer, sunrise, BUYER_CLAIM_CODE, sunriseW.end(), pandesal, 2, ensaymada, 1);
        reservation(mara, sunrise, "730118", sunriseW.end(), pandesal, 1, null, 0);
        Reservation junsPickup = reservation(jun, sunrise, "559020", sunriseW.end(), cheeseRolls, 2, null, 0);
        junsPickup.complete(now.minus(Duration.ofMinutes(20)));
        reservations.save(junsPickup);

        // The demo buyer's history: one picked up, one cancelled, one missed (it counts toward the 2-strike rule).
        Reservation yesterday = reservation(buyer, lola, "201944", now, adobo, 1, null, 0);
        yesterday.complete(now);
        reservations.save(yesterday);
        backdate(yesterday, now.minus(Duration.ofDays(1)).minus(Duration.ofHours(3)), now.minus(Duration.ofDays(1)));

        Reservation cancelled = reservation(buyer, burnham, "318260", now, taho, 2, null, 0);
        cancelled.cancel();
        reservations.save(cancelled);
        backdate(cancelled, now.minus(Duration.ofDays(6)), null);

        Reservation missed = reservation(buyer, pinegrove, "664102", now, bananaBread, 1, null, 0);
        missed.expire();
        reservations.save(missed);
        backdate(missed, now.minus(Duration.ofDays(8)), null);

        log.debug("Seeded demo reservation {} for {}", mine.getId(), BUYER);
    }

    private record Window(Instant start, Instant end) {
    }

    private User user(String username, Role role, boolean approved, String passwordHash) {
        User user = new User(username, passwordHash, role);
        if (approved) {
            user.approve();
        }
        return users.save(user);
    }

    private Item item(User owner, String name, String description, String price, String usual,
                      int quantity, Window window, String where) {
        return items.save(new Item(owner, name, description, new BigDecimal(price), new BigDecimal(usual),
                quantity, window.end().plus(Duration.ofHours(2)), where, window.start(), window.end()));
    }

    private Reservation reservation(User buyer, User owner, String code, Instant deadline,
                                    Item first, int firstQty, Item second, int secondQty) {
        Reservation reservation = new Reservation(buyer, owner, code, deadline);
        reservation.addItem(new ReservationItem(first, firstQty));
        if (second != null) {
            reservation.addItem(new ReservationItem(second, secondQty));
        }
        return reservations.save(reservation);
    }

    /** created_at is set on insert, so past reservations get their real times afterwards. */
    private void backdate(Reservation reservation, Instant createdAt, Instant completedAt) {
        reservations.flush();
        jdbc.update("UPDATE reservations SET created_at = ?, pickup_deadline = ?, completed_at = ? WHERE id = ?",
                Timestamp.from(createdAt), Timestamp.from(createdAt.plus(Duration.ofHours(3))),
                completedAt == null ? null : Timestamp.from(completedAt), reservation.getId());
    }
}
