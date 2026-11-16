package com.thriftbite.backend.demo;

import com.thriftbite.backend.live.LiveChange.DealsChanged;
import org.springframework.context.ApplicationEventPublisher;
import com.thriftbite.backend.alert.Alerts;
import com.thriftbite.backend.item.Item;
import com.thriftbite.backend.item.ItemRepository;
import com.thriftbite.backend.photo.PhotoProcessor;
import com.thriftbite.backend.photo.PhotoProcessor.Processed;
import com.thriftbite.backend.photo.Photos;
import com.thriftbite.backend.reservation.Reservation;
import com.thriftbite.backend.reservation.ReservationItem;
import com.thriftbite.backend.reservation.ReservationRepository;
import com.thriftbite.backend.store.OpenHours;
import com.thriftbite.backend.store.Store;
import com.thriftbite.backend.store.StoreService;
import com.thriftbite.backend.user.Role;
import com.thriftbite.backend.user.User;
import com.thriftbite.backend.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
    private final StoreService stores;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ApplicationEventPublisher live;
    private final Alerts alerts;
    private final Photos photos;
    private final PhotoProcessor photoProcessor;
    /** Demo photos, shrunk once and reused by every reset. Empty means "no such file". */
    private final Map<String, Optional<DemoPhoto>> demoPhotos = new ConcurrentHashMap<>();
    private final String keepAdmin;
    private final Duration maxAge;

    private volatile Instant seededAt;

    public DemoData(UserRepository users, ItemRepository items, ReservationRepository reservations,
                    PasswordEncoder passwordEncoder, StoreService stores, JdbcTemplate jdbc,
                    PlatformTransactionManager transactions, ApplicationEventPublisher live, Alerts alerts,
                    Photos photos, PhotoProcessor photoProcessor,
                    @Value("${app.admin.username:}") String bootstrapAdmin,
                    @Value("${app.demo.max-age:PT6H}") Duration maxAge) {
        this.users = users;
        this.items = items;
        this.reservations = reservations;
        this.passwordEncoder = passwordEncoder;
        this.stores = stores;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactions);
        this.live = live;
        this.alerts = alerts;
        this.photos = photos;
        this.photoProcessor = photoProcessor;
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
            live.publishEvent(new DealsChanged());   // open browse pages reload once the new data is in
        });
        seededAt = Instant.now();
        log.info("Demo data reset; next reset after {}", seededAt.plus(maxAge));
    }

    private void wipe() {
        jdbc.update("DELETE FROM admin_actions WHERE demo");
        jdbc.update("DELETE FROM cart_items");
        jdbc.update("DELETE FROM item_events");
        jdbc.update("DELETE FROM reservation_items");
        jdbc.update("DELETE FROM reservations");
        jdbc.update("DELETE FROM items");
        jdbc.update("DELETE FROM store_members");
        jdbc.update("DELETE FROM stores");
        jdbc.update("DELETE FROM users WHERE username <> ?", keepAdmin);
    }

    private void seed(Instant now) {
        // One random password nobody knows, hashed once (BCrypt is slow on purpose, and Render's
        // free CPU is small). Demo accounts are only reached through /api/demo/login.
        String unusableHash = passwordEncoder.encode(UUID.randomUUID().toString());
        User buyer = user(BUYER, Role.CONSUMER, true, unusableHash);
        User mara = user("mara.l", Role.CONSUMER, true, unusableHash);
        User jun = user("jun.d", Role.CONSUMER, true, unusableHash);
        // regulars who show up in Sunrise Bakery's past sales
        List<User> regulars = List.of(mara, jun,
                user("kat.r", Role.CONSUMER, true, unusableHash),
                user("paolo.g", Role.CONSUMER, true, unusableHash),
                user("bea.s", Role.CONSUMER, true, unusableHash));
        user(ADMIN, Role.ADMIN, true, unusableHash);

        User rosa = user(OWNER, Role.OWNER, true, unusableHash);
        Store sunrise = stores.createForOwner(rosa, "Sunrise Bakery");
        Map<DayOfWeek, OpenHours> sunriseHours = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            sunriseHours.put(day, new OpenHours(LocalTime.of(7, 0), LocalTime.of(day == DayOfWeek.FRIDAY ? 20 : 19, 0)));
        }
        sunrise.updateProfile("Sunrise Bakery", "112 Session Road", "Session Road Area",
                "Green awning, two doors up from the jeepney stop", true,
                LocalTime.of(17, 0), LocalTime.of(19, 0), sunriseHours);
        Shop lola = shop("lola.ising", "Lola Ising's Carinderia", true, unusableHash);
        lola.store().locate("48 Bonifacio Street", "Across from the tricycle terminal", null, null);
        Shop burnham = shop("burnham.taho", "Burnham Taho Cart", true, unusableHash);
        burnham.store().locate("Burnham Park", "Near the lake, by the boat rentals", null, null);
        Shop pinegrove = shop("pinegrove.cafe", "Pine Grove Café", true, unusableHash);
        pinegrove.store().locate("21 Legarda Road", null, null, null);
        pinegrove.store().acceptGcash(true);
        Shop kusina = shop("kusina.ading", "Kusina ni Ading", true, unusableHash);
        kusina.store().locate("7 Abanao Street", null, null, null);
        // waiting for the admin, so the approvals screen has something on it
        shop("manang.fe.bakeshop", "Manang Fe's Bakeshop", false, unusableHash);
        shop("aurora.carinderia", "Aurora Carinderia", false, unusableHash);
        shop("hillside.cafe", "Hillside Café", false, unusableHash);

        // Windows are anchored to the top of the current hour in Manila, so every one is still open.
        Instant hour = now.atZone(MANILA).truncatedTo(ChronoUnit.HOURS).toInstant();
        Window burnhamW = new Window(hour.minus(Duration.ofMinutes(60)), hour.plus(Duration.ofMinutes(90)));
        Window sunriseW = new Window(hour.minus(Duration.ofMinutes(30)), hour.plus(Duration.ofMinutes(150)));
        Window lolaW = new Window(hour, hour.plus(Duration.ofMinutes(180)));
        Window pinegroveW = new Window(hour, hour.plus(Duration.ofMinutes(180)));
        Window kusinaW = new Window(hour.plus(Duration.ofMinutes(30)), hour.plus(Duration.ofMinutes(210)));

        String sunriseAt = "Sunrise Bakery counter, Session Road";
        Item pandesal = item(sunrise, rosa, "Pandesal, bag of 10", "Baked this morning.", "25.00", "50.00", 3, sunriseW, sunriseAt);
        Item ensaymada = item(sunrise, rosa, "Ube cheese ensaymada, box of 4", null, "90.00", "150.00", 2, sunriseW, sunriseAt);
        item(sunrise, rosa, "Spanish bread, 6 pcs", null, "40.00", "70.00", 4, sunriseW, sunriseAt);
        Item cheeseRolls = item(sunrise, rosa, "Cheese rolls, 6 pcs", null, "45.00", "90.00", 0, sunriseW, sunriseAt);

        String lolaAt = "Lola Ising's Carinderia, Bonifacio Street";
        Item adobo = item(lola.store(), lola.owner(), "Chicken adobo with rice", "One serving.", "60.00", "95.00", 6, lolaW, lolaAt);
        Item pinakbet = item(lola.store(), lola.owner(), "Pinakbet family tray", "Good for 4.", "120.00", "220.00", 2, lolaW, lolaAt);

        Item taho = item(burnham.store(), burnham.owner(), "Strawberry taho, 2 cups", null, "20.00", "35.00", 8, burnhamW, "Burnham Park, near the lake");

        String pinegroveAt = "Pine Grove Café, Legarda Road";
        Item bananaBread = item(pinegrove.store(), pinegrove.owner(), "Banana bread loaf", null, "70.00", "140.00", 3, pinegroveW, pinegroveAt);
        Item croissants = item(pinegrove.store(), pinegrove.owner(), "Ham and cheese croissants, 2 pcs", null, "45.00", "90.00", 4, pinegroveW, pinegroveAt);

        Item lumpia = item(kusina.store(), kusina.owner(), "Lumpiang shanghai, 10 pcs", null, "55.00", "90.00", 5, kusinaW, "Kusina ni Ading, Abanao Street");

        // Today at Sunrise Bakery: two pickups waiting, one handed over.
        // (Stock above already has these taken out: 6 pandesal listed, 3 reserved; 3 ensaymada, 1 reserved.)
        Reservation mine = reservation(buyer, sunrise, BUYER_CLAIM_CODE, sunriseW.end(), pandesal, 2, ensaymada, 1);
        reservation(mara, sunrise, "730118", sunriseW.end(), pandesal, 1, null, 0);
        Reservation junsPickup = reservation(jun, sunrise, "559020", sunriseW.end(), cheeseRolls, 2, null, 0);
        junsPickup.complete(now.minus(Duration.ofMinutes(20)));
        reservations.save(junsPickup);

        // Stock history, so sales numbers have something to count: what each item started with...
        reservations.flush();   // plain SQL below doesn't see JPA's pending inserts until they're flushed
        jdbc.update("""
                INSERT INTO item_events (item_id, store_id, type, qty_delta, qty_after, occurred_at)
                SELECT i.id, i.store_id, 'LISTED', i.quantity + COALESCE(t.qty, 0), i.quantity + COALESCE(t.qty, 0),
                       i.pickup_start - interval '2 hours'
                  FROM items i
                  LEFT JOIN (SELECT item_id, SUM(quantity) AS qty FROM reservation_items GROUP BY item_id) t
                    ON t.item_id = i.id
                """);
        // ...and the cheese rolls selling out 35 minutes ago
        jdbc.update("""
                INSERT INTO item_events (item_id, store_id, type, qty_delta, qty_after, occurred_at)
                VALUES (?, ?, 'SOLD_OUT', 0, 0, ?)
                """, cheeseRolls.getId(), sunrise.getId(), Timestamp.from(now.minus(Duration.ofMinutes(35))));

        // 30 days of past sales at Sunrise Bakery, for the Sales screen
        new DemoSalesHistory(jdbc, MANILA).seed(sunrise.getId(), rosa.getId(),
                regulars.stream().map(User::getId).toList(), now.atZone(MANILA).toLocalDate(), 30, sunriseAt);

        // The demo buyer's history: one picked up, one cancelled, one missed (it counts toward the 2-strike rule).
        Reservation yesterday = reservation(buyer, lola.store(), "201944", now, adobo, 1, null, 0);
        yesterday.complete(now);
        reservations.save(yesterday);
        backdate(yesterday, now.minus(Duration.ofDays(1)).minus(Duration.ofHours(3)), now.minus(Duration.ofDays(1)));

        Reservation cancelled = reservation(buyer, burnham.store(), "318260", now, taho, 2, null, 0);
        cancelled.cancel(now);
        reservations.save(cancelled);
        backdate(cancelled, now.minus(Duration.ofDays(6)), null);

        Reservation missed = reservation(buyer, pinegrove.store(), "664102", now, bananaBread, 1, null, 0);
        missed.expire(now);
        reservations.save(missed);
        backdate(missed, now.minus(Duration.ofDays(8)), null);

        // ...and a couple of months of pickups, so the account page has savings to show.
        pickedUp(buyer, lola.store(), "118452", adobo, 2, now.minus(Duration.ofDays(12)));
        pickedUp(buyer, pinegrove.store(), "902317", croissants, 1, now.minus(Duration.ofDays(19)));
        Reservation lumpiaPickup = pickedUp(buyer, kusina.store(), "475560", lumpia, 1, now.minus(Duration.ofDays(33)));
        pickedUp(buyer, lola.store(), "660913", pinakbet, 1, now.minus(Duration.ofDays(40)));
        pickedUp(buyer, burnham.store(), "239874", taho, 2, now.minus(Duration.ofDays(55)));
        pickedUp(buyer, pinegrove.store(), "781245", bananaBread, 1, now.minus(Duration.ofDays(61)));

        // The buyer follows three stores and has alerts waiting; a few regulars follow the bakery too.
        for (Store followed : List.of(sunrise, lola.store(), pinegrove.store())) {
            jdbc.update("INSERT INTO store_follows (user_id, store_id) VALUES (?, ?)", buyer.getId(), followed.getId());
        }
        for (User regular : regulars) {
            jdbc.update("INSERT INTO store_follows (user_id, store_id) VALUES (?, ?)", regular.getId(), sunrise.getId());
        }
        alerts.missed(missed.getId(), null);
        alerts.pickedUp(yesterday.getId());
        // those two are old news: dated when they happened, and already read
        jdbc.update("""
                UPDATE alerts a SET created_at = COALESCE(r.completed_at, r.expired_at), read_at = now()
                  FROM reservations r WHERE r.id = a.reservation_id AND a.user_id = ?
                """, buyer.getId());
        alerts.dealsPosted(pinegrove.store().getId());
        alerts.dealsPosted(lola.store().getId());
        alerts.dealsPosted(sunrise.getId());
        alerts.reserved(mine.getId());
        // staggered through the afternoon: each store posting, then the pickup reminder
        String posted = "UPDATE alerts SET created_at = now() - ?::interval WHERE user_id = ? AND store_id = ? AND kind = 'DEAL'";
        jdbc.update(posted, "40 minutes", buyer.getId(), sunrise.getId());
        jdbc.update(posted, "85 minutes", buyer.getId(), lola.store().getId());
        jdbc.update(posted, "2 hours", buyer.getId(), pinegrove.store().getId());

        // Problem reports: one of the buyer's that an admin has answered, and one still waiting.
        jdbc.update("""
                INSERT INTO reports (reporter_id, store_id, reservation_id, kind, details, status, reply, created_at, resolved_at)
                VALUES (?, ?, ?, 'NOT_AS_LISTED', ?, 'RESOLVED', ?, ?, ?)
                """, buyer.getId(), kusina.store().getId(), lumpiaPickup.getId(),
                "The pack had 8 pieces, not the 10 in the listing.",
                "Thanks for telling us. We spoke with the store, and the listing now says what's in the pack.",
                Timestamp.from(now.minus(Duration.ofDays(33))), Timestamp.from(now.minus(Duration.ofDays(31))));
        jdbc.update("""
                INSERT INTO reports (reporter_id, store_id, kind, details, created_at)
                VALUES (?, ?, 'OVERCHARGED', ?, ?)
                """, mara.getId(), kusina.store().getId(),
                "I was asked for ₱65 at the counter. The app said ₱55.", Timestamp.from(now.minus(Duration.ofDays(2))));

        addPhotos();

        // The buyer has been around as long as their history.
        jdbc.update("UPDATE users SET created_at = ? WHERE id = ?",
                Timestamp.from(now.minus(Duration.ofDays(75))), buyer.getId());

        log.debug("Seeded demo reservation {} for {}", mine.getId(), BUYER);
    }

    private record Window(Instant start, Instant end) {
    }

    private User user(String username, Role role, boolean approved, String passwordHash) {
        User user = new User(username, passwordHash, role);
        user.markDemo();
        if (approved) {
            user.approve();
        }
        return users.save(user);
    }

    /** A store and the owner account that runs it. */
    private record Shop(Store store, User owner) {
    }

    /** An approved (or still waiting) owner account with its store. */
    private Shop shop(String username, String storeName, boolean approved, String passwordHash) {
        User owner = user(username, Role.OWNER, approved, passwordHash);
        return new Shop(stores.createForOwner(owner, storeName), owner);
    }

    private Item item(Store store, User listedBy, String name, String description, String price, String usual,
                      int quantity, Window window, String where) {
        return items.save(new Item(store, listedBy, name, description, new BigDecimal(price), new BigDecimal(usual),
                quantity, window.end().plus(Duration.ofHours(2)), where, window.start(), window.end()));
    }

    private Reservation reservation(User buyer, Store store, String code, Instant deadline,
                                    Item first, int firstQty, Item second, int secondQty) {
        Reservation reservation = new Reservation(buyer, store, code, deadline);
        reservation.addItem(new ReservationItem(first, firstQty));
        if (second != null) {
            reservation.addItem(new ReservationItem(second, secondQty));
        }
        return reservations.save(reservation);
    }

    /** A reservation the buyer picked up at {@code when}, three hours after making it. */
    private Reservation pickedUp(User buyer, Store store, String code, Item item, int quantity, Instant when) {
        Reservation reservation = reservation(buyer, store, code, when, item, quantity, null, 0);
        reservation.complete(when);
        reservations.save(reservation);
        backdate(reservation, when.minus(Duration.ofHours(3)), when);
        return reservation;
    }

    private record DemoPhoto(String sha256, Processed photo) {
    }

    /**
     * Gives demo listings and stores their photos, from files in src/main/resources/demo/photos.
     * A listing called "Pandesal, bag of 10" uses pandesal.jpg (the name up to the first comma);
     * a store uses store-&lt;its link name&gt;.jpg, e.g. store-sunrise-bakery.jpg. Anything without
     * a file simply has no photo, so the demo works with none, some or all of them.
     */
    private void addPhotos() {
        reservations.flush();   // the plain SQL below needs the stores and listings in the database
        int[] used = {0};
        jdbc.query("SELECT id, slug FROM stores", (rs, row) -> Map.entry(rs.getObject("id", UUID.class), rs.getString("slug")))
                .forEach(store -> {
                    demoPhoto("store-" + store.getValue()).ifPresent(photo -> {
                        UUID photoId = photos.insert(store.getKey(), null, photo.sha256(), photo.photo()).id();
                        jdbc.update("UPDATE stores SET photo_id = ? WHERE id = ?", photoId, store.getKey());
                        used[0]++;
                    });
                    for (String name : jdbc.queryForList("SELECT DISTINCT name FROM items WHERE store_id = ?", String.class, store.getKey())) {
                        demoPhoto(fileNameFor(name)).ifPresent(photo -> {
                            UUID photoId = photos.insert(store.getKey(), null, photo.sha256(), photo.photo()).id();
                            jdbc.update("UPDATE items SET photo_id = ? WHERE store_id = ? AND name = ?", photoId, store.getKey(), name);
                            used[0]++;
                        });
                    }
                });
        log.info("Demo photos: {} found in demo/photos", used[0]);
    }

    /** "Ube cheese ensaymada, box of 4" becomes "ube-cheese-ensaymada". */
    static String fileNameFor(String listingName) {
        String food = listingName.contains(",") ? listingName.substring(0, listingName.indexOf(',')) : listingName;
        return food.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    private Optional<DemoPhoto> demoPhoto(String fileName) {
        return demoPhotos.computeIfAbsent(fileName, name -> {
            ClassPathResource file = new ClassPathResource("demo/photos/" + name + ".jpg");
            if (!file.exists()) {
                return Optional.empty();
            }
            try (InputStream in = file.getInputStream()) {
                byte[] bytes = in.readAllBytes();
                return Optional.of(new DemoPhoto(Photos.sha256(bytes), photoProcessor.process(bytes)));
            } catch (IOException | RuntimeException e) {
                log.warn("Demo photo {}.jpg can't be used: {}", name, e.getMessage());
                return Optional.empty();
            }
        });
    }

    /** Moves a finished reservation into the past: made at {@code createdAt}, ended three hours later. */
    private void backdate(Reservation reservation, Instant createdAt, Instant completedAt) {
        reservations.flush();
        Timestamp ended = Timestamp.from(createdAt.plus(Duration.ofHours(3)));
        jdbc.update("""
                UPDATE reservations
                   SET created_at = ?, pickup_deadline = ?, completed_at = ?,
                       cancelled_at = CASE WHEN cancelled_at IS NULL THEN NULL ELSE ?::timestamptz END,
                       expired_at   = CASE WHEN expired_at   IS NULL THEN NULL ELSE ?::timestamptz END
                 WHERE id = ?
                """,
                Timestamp.from(createdAt), ended, completedAt == null ? null : Timestamp.from(completedAt),
                ended, ended, reservation.getId());
    }
}
