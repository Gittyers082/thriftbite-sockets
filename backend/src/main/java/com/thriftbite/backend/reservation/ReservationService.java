package com.thriftbite.backend.reservation;

import com.thriftbite.backend.cart.CartItem;
import com.thriftbite.backend.cart.CartItemRepository;
import com.thriftbite.backend.common.ApiExceptions.BadRequestException;
import com.thriftbite.backend.common.ApiExceptions.ConflictException;
import com.thriftbite.backend.common.ApiExceptions.NotFoundException;
import com.thriftbite.backend.common.ApiExceptions.TooManyRequestsException;
import com.thriftbite.backend.common.ApiExceptions.WrongClaimCodeException;
import com.thriftbite.backend.item.Item;
import com.thriftbite.backend.item.ItemRepository;
import com.thriftbite.backend.item.ItemStatus;
import com.thriftbite.backend.item.StockEventType;
import com.thriftbite.backend.item.StockEvents;
import com.thriftbite.backend.reservation.dto.ReservationResponse;
import com.thriftbite.backend.store.StoreRepository;
import com.thriftbite.backend.store.StoreService;
import com.thriftbite.backend.user.User;
import com.thriftbite.backend.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReservationService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CartItemRepository cart;
    private final ItemRepository items;
    private final ReservationRepository reservations;
    private final UserRepository users;
    private final StoreRepository storeRepository;
    private final StoreService stores;
    private final StockEvents events;
    private final int maxActivePerBuyer;
    private final int maxCodeAttempts;
    private final Duration codeLock;

    public ReservationService(CartItemRepository cart, ItemRepository items,
                              ReservationRepository reservations, UserRepository users,
                              StoreRepository storeRepository, StoreService stores, StockEvents events,
                              @Value("${app.reservations.max-active-per-buyer:2}") int maxActivePerBuyer,
                              @Value("${app.reservations.max-code-attempts:5}") int maxCodeAttempts,
                              @Value("${app.reservations.code-lock:PT15M}") Duration codeLock) {
        this.cart = cart;
        this.items = items;
        this.reservations = reservations;
        this.users = users;
        this.storeRepository = storeRepository;
        this.stores = stores;
        this.events = events;
        this.maxActivePerBuyer = maxActivePerBuyer;
        this.maxCodeAttempts = maxCodeAttempts;
        this.codeLock = codeLock;
    }

    /** What we need from a cart line, copied out before the stock updates clear the session. */
    private record Wanted(UUID itemId, UUID storeId, String name, int quantity) {
    }

    /** Stock going back on sale from one reservation line. */
    private record Release(UUID itemId, int quantity, UUID reservationId) {
    }

    /**
     * Turns the buyer's whole cart into reservations, all or nothing.
     *
     * Every line takes its stock with one atomic UPDATE. If any line can't be
     * filled, the exception rolls back the whole transaction: stock already
     * taken for earlier lines goes back, no reservation or stock event is
     * saved, and the cart stays as it was.
     *
     * A buyer can hold only a few reservations at once, because reserving
     * costs nothing (buyers pay at the counter). Without a limit, one account
     * could hold every store's food and never show up.
     */
    @Transactional
    public List<ReservationResponse> checkout(UUID buyerId) {
        // Lock the buyer's row first, so two checkouts by the same buyer (two tabs,
        // a double tap) run one after the other and both see the real count.
        users.lockById(buyerId).orElseThrow(() -> new NotFoundException("User no longer exists"));

        List<CartItem> lines = cart.findByIdUserId(buyerId);
        if (lines.isEmpty()) {
            throw new BadRequestException("Your cart is empty");
        }

        // Lock rows in a fixed order (by item id). If two checkouts share items,
        // they then wait for each other instead of deadlocking.
        List<Wanted> wanted = lines.stream()
                .map(line -> new Wanted(line.getItem().getId(), line.getItem().getStore().getId(),
                        line.getItem().getName(), line.getQuantity()))
                .sorted(Comparator.comparing(Wanted::itemId))
                .toList();

        long active = reservations.countByBuyerIdAndStatus(buyerId, ReservationStatus.RESERVED);
        long adding = wanted.stream().map(Wanted::storeId).distinct().count();
        if (active + adding > maxActivePerBuyer) {
            throw new ConflictException(reservationLimitMessage(active, adding));
        }

        // One reservation per store: each store hands over its own food with its own claim code.
        // Their ids are picked now, so every stock event below can point at its reservation.
        Map<UUID, UUID> reservationIdByStore = new LinkedHashMap<>();
        wanted.forEach(w -> reservationIdByStore.computeIfAbsent(w.storeId(), store -> UUID.randomUUID()));

        Instant now = Instant.now();
        for (Wanted w : wanted) {
            UUID reservationId = reservationIdByStore.get(w.storeId());
            int reserved = items.reserveStock(w.itemId(), w.quantity(), now, ItemStatus.AVAILABLE);
            if (reserved == 0) {
                throw new ConflictException("'" + w.name() + "' doesn't have " + w.quantity() + " left anymore");
            }
            events.record(w.itemId(), StockEventType.RESERVED, -w.quantity(), reservationId, buyerId);
            if (items.markSoldOutIfEmpty(w.itemId(), ItemStatus.SOLD_OUT) == 1) {
                events.record(w.itemId(), StockEventType.SOLD_OUT, 0, reservationId, buyerId);
            }
        }

        // The stock updates cleared the session, so load fresh copies to build reservations.
        Map<UUID, Item> itemsById = items.findAllById(wanted.stream().map(Wanted::itemId).toList()).stream()
                .collect(Collectors.toMap(Item::getId, Function.identity()));
        User buyer = users.getReferenceById(buyerId);
        Map<UUID, List<Wanted>> byStore = wanted.stream()
                .collect(Collectors.groupingBy(Wanted::storeId, LinkedHashMap::new, Collectors.toList()));

        List<Reservation> created = new ArrayList<>();
        for (Map.Entry<UUID, List<Wanted>> entry : byStore.entrySet()) {
            List<Item> storeItems = entry.getValue().stream().map(w -> itemsById.get(w.itemId())).toList();
            Instant pickupDeadline = storeItems.stream().map(Item::getPickupEnd).min(Instant::compareTo).orElseThrow();

            Reservation reservation = new Reservation(reservationIdByStore.get(entry.getKey()), buyer,
                    storeRepository.getReferenceById(entry.getKey()), newClaimCode(), pickupDeadline);
            for (Wanted w : entry.getValue()) {
                reservation.addItem(new ReservationItem(itemsById.get(w.itemId()), w.quantity()));
            }
            created.add(reservations.save(reservation));
        }

        cart.deleteAllForUser(buyerId);
        return created.stream().map(ReservationResponse::forBuyer).toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> listForBuyer(UUID buyerId) {
        return reservations.findByBuyerIdOrderByCreatedAtDesc(buyerId).stream()
                .map(ReservationResponse::forBuyer)
                .toList();
    }

    /** Buyer changes their mind before pickup: the food goes back on sale. */
    @Transactional
    public ReservationResponse cancel(UUID buyerId, UUID reservationId) {
        Reservation reservation = reservations.findWithDetailsById(reservationId)
                .filter(r -> r.getBuyer().getId().equals(buyerId))
                .orElseThrow(() -> new NotFoundException("Reservation not found"));
        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            throw new ConflictException("Only active reservations can be cancelled");
        }

        Instant now = Instant.now();
        reservation.cancel(now);
        ReservationResponse response = ReservationResponse.forBuyer(reservation);
        // The first restock flushes the cancellation above before clearing the session.
        restock(releasesOf(List.of(reservation)), now, buyerId);
        return response;
    }

    /**
     * Buyers who missed their pickup deadline lose the reservation, and the
     * food goes back on sale if it hasn't expired. Called by ExpiryJob.
     *
     * @return how many reservations expired
     */
    @Transactional
    public int expireMissedPickups(Instant now) {
        List<Reservation> missed = reservations.findByStatusAndPickupDeadlineBefore(ReservationStatus.RESERVED, now);
        missed.forEach(reservation -> reservation.expire(now));
        List<Release> releases = releasesOf(missed);
        // The first restock flushes the status changes above before clearing the session.
        restock(releases, now, null);
        if (releases.isEmpty()) {
            reservations.flush();
        }
        return missed.size();
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> listActiveForOwner(UUID userId) {
        return reservations.findByStoreIdAndStatusOrderByPickupDeadlineAsc(stores.storeIdFor(userId),
                        ReservationStatus.RESERVED)
                .stream()
                .map(ReservationResponse::forOwner)
                .toList();
    }

    /**
     * The store hands over the food after checking the buyer's claim code.
     *
     * Wrong codes are counted, and after {@code maxCodeAttempts} in a row code
     * entry pauses. The transaction still commits on a wrong code
     * (noRollbackFor), otherwise the count would be rolled back with the error.
     */
    @Transactional(noRollbackFor = WrongClaimCodeException.class)
    public ReservationResponse complete(UUID userId, UUID reservationId, String claimCode) {
        UUID storeId = stores.storeIdFor(userId);
        Reservation reservation = reservations.findWithDetailsById(reservationId)
                .filter(r -> r.getStore().getId().equals(storeId))
                .orElseThrow(() -> new NotFoundException("Reservation not found"));
        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            throw new ConflictException("This reservation is already " + reservation.getStatus().name().toLowerCase());
        }
        Instant now = Instant.now();
        if (reservation.isCodeLocked(now)) {
            throw new TooManyRequestsException(
                    "Too many wrong codes. Code entry for this reservation is paused for a few minutes.",
                    Duration.between(now, reservation.getCodeLockedUntil()), "claim_code_locked");
        }
        if (!codesMatch(reservation.getClaimCode(), claimCode)) {
            int left = reservation.recordWrongCode(now, maxCodeAttempts, codeLock);
            throw new WrongClaimCodeException(left == 0
                    ? "Wrong claim code. Code entry is paused for " + codeLock.toMinutes() + " minutes."
                    : "Wrong claim code. " + left + (left == 1 ? " try" : " tries") + " left.");
        }
        reservation.complete(now);
        return ReservationResponse.forOwner(reservation);
    }

    private static List<Release> releasesOf(List<Reservation> ended) {
        List<Release> releases = new ArrayList<>();
        for (Reservation reservation : ended) {
            reservation.getItems().forEach(line ->
                    releases.add(new Release(line.getItem().getId(), line.getQuantity(), reservation.getId())));
        }
        // fixed order again, to avoid deadlocks with checkouts
        releases.sort(Comparator.comparing(Release::itemId));
        return releases;
    }

    private void restock(List<Release> releases, Instant now, UUID actorId) {
        for (Release r : releases) {
            items.restock(r.itemId(), r.quantity(), now);
            events.record(r.itemId(), StockEventType.RELEASED, r.quantity(), r.reservationId(), actorId);
            items.markAvailableIfRestocked(r.itemId(), ItemStatus.AVAILABLE, ItemStatus.SOLD_OUT);
        }
    }

    private String reservationLimitMessage(long active, long adding) {
        String limit = "You can hold " + maxActivePerBuyer + " reservations at a time";
        if (active == 0) {
            return limit + ", and this cart has food from " + adding
                    + " stores. Remove one store's food to reserve the rest.";
        }
        return limit + " and you already have " + active + ". Pick up or cancel one first.";
    }

    static String newClaimCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    /** Constant-time comparison, so response timing doesn't hint at how many digits were right. */
    private static boolean codesMatch(String expected, String given) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8));
    }
}
