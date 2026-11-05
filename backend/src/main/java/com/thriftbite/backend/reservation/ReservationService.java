package com.thriftbite.backend.reservation;

import com.thriftbite.backend.cart.CartItem;
import com.thriftbite.backend.cart.CartItemRepository;
import com.thriftbite.backend.common.ApiExceptions.BadRequestException;
import com.thriftbite.backend.common.ApiExceptions.ConflictException;
import com.thriftbite.backend.common.ApiExceptions.NotFoundException;
import com.thriftbite.backend.item.Item;
import com.thriftbite.backend.item.ItemRepository;
import com.thriftbite.backend.item.ItemStatus;
import com.thriftbite.backend.reservation.dto.ReservationResponse;
import com.thriftbite.backend.user.User;
import com.thriftbite.backend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
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

    public ReservationService(CartItemRepository cart, ItemRepository items,
                              ReservationRepository reservations, UserRepository users) {
        this.cart = cart;
        this.items = items;
        this.reservations = reservations;
        this.users = users;
    }

    /** What we need from a cart line, copied out before the stock updates clear the session. */
    private record Wanted(UUID itemId, String name, int quantity) {
    }

    /**
     * Turns the buyer's whole cart into reservations, all or nothing.
     *
     * Every line takes its stock with one atomic UPDATE. If any line can't be
     * filled, the exception rolls back the whole transaction: stock already
     * taken for earlier lines goes back, no reservation is saved, and the cart
     * stays as it was.
     */
    @Transactional
    public List<ReservationResponse> checkout(UUID buyerId) {
        List<CartItem> lines = cart.findByIdUserId(buyerId);
        if (lines.isEmpty()) {
            throw new BadRequestException("Your cart is empty");
        }

        // Lock rows in a fixed order (by item id). If two checkouts share items,
        // they then wait for each other instead of deadlocking.
        List<Wanted> wanted = lines.stream()
                .map(line -> new Wanted(line.getItem().getId(), line.getItem().getName(), line.getQuantity()))
                .sorted(Comparator.comparing(Wanted::itemId))
                .toList();

        Instant now = Instant.now();
        for (Wanted w : wanted) {
            int reserved = items.reserveStock(w.itemId(), w.quantity(), now, ItemStatus.AVAILABLE);
            if (reserved == 0) {
                throw new ConflictException("'" + w.name() + "' doesn't have " + w.quantity() + " left anymore");
            }
            items.markSoldOutIfEmpty(w.itemId(), ItemStatus.SOLD_OUT);
        }

        // The stock updates cleared the session, so load fresh copies (with owners) to build reservations.
        Map<UUID, Item> itemsById = items.findAllById(wanted.stream().map(Wanted::itemId).toList()).stream()
                .collect(Collectors.toMap(Item::getId, Function.identity()));
        User buyer = users.getReferenceById(buyerId);

        // One reservation per owner: each store hands over its own food with its own claim code.
        Map<UUID, List<Wanted>> byOwner = wanted.stream().collect(Collectors.groupingBy(
                w -> itemsById.get(w.itemId()).getOwner().getId(), LinkedHashMap::new, Collectors.toList()));

        List<Reservation> created = new ArrayList<>();
        for (List<Wanted> ownerLines : byOwner.values()) {
            List<Item> ownerItems = ownerLines.stream().map(w -> itemsById.get(w.itemId())).toList();
            Instant pickupDeadline = ownerItems.stream().map(Item::getPickupEnd).min(Instant::compareTo).orElseThrow();

            Reservation reservation = new Reservation(buyer, ownerItems.getFirst().getOwner(),
                    newClaimCode(), pickupDeadline);
            for (Wanted w : ownerLines) {
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

        reservation.cancel();
        ReservationResponse response = ReservationResponse.forBuyer(reservation);
        List<Wanted> toRestock = reservation.getItems().stream()
                .map(line -> new Wanted(line.getItem().getId(), line.getItemName(), line.getQuantity()))
                .sorted(Comparator.comparing(Wanted::itemId))
                .toList();

        // The first restock flushes the cancellation above before clearing the session.
        Instant now = Instant.now();
        for (Wanted w : toRestock) {
            items.restock(w.itemId(), w.quantity(), now);
            items.markAvailableIfRestocked(w.itemId(), ItemStatus.AVAILABLE, ItemStatus.SOLD_OUT);
        }
        return response;
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> listActiveForOwner(UUID ownerId) {
        return reservations.findByOwnerIdAndStatusOrderByPickupDeadlineAsc(ownerId, ReservationStatus.RESERVED)
                .stream()
                .map(ReservationResponse::forOwner)
                .toList();
    }

    /** Owner hands over the food after checking the buyer's claim code. */
    @Transactional
    public ReservationResponse complete(UUID ownerId, UUID reservationId, String claimCode) {
        Reservation reservation = reservations.findWithDetailsById(reservationId)
                .filter(r -> r.getOwner().getId().equals(ownerId))
                .orElseThrow(() -> new NotFoundException("Reservation not found"));
        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            throw new ConflictException("This reservation is already " + reservation.getStatus().name().toLowerCase());
        }
        if (!codesMatch(reservation.getClaimCode(), claimCode)) {
            throw new BadRequestException("Wrong claim code");
        }
        reservation.complete(Instant.now());
        return ReservationResponse.forOwner(reservation);
    }

    static String newClaimCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    /** Constant-time comparison, so response timing doesn't hint at how many digits were right. */
    private static boolean codesMatch(String expected, String given) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8));
    }
}
