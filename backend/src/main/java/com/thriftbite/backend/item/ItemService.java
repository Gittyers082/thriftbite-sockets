package com.thriftbite.backend.item;

import com.thriftbite.backend.common.ApiExceptions.BadRequestException;
import com.thriftbite.backend.common.ApiExceptions.ConflictException;
import com.thriftbite.backend.common.ApiExceptions.ForbiddenException;
import com.thriftbite.backend.common.ApiExceptions.NotFoundException;
import com.thriftbite.backend.common.PageResponse;
import com.thriftbite.backend.item.dto.ItemRequest;
import com.thriftbite.backend.item.dto.ItemResponse;
import com.thriftbite.backend.store.StoreRepository;
import com.thriftbite.backend.store.StoreService;
import com.thriftbite.backend.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ItemService {

    static final int MAX_PAGE_SIZE = 50;

    private final ItemRepository items;
    private final UserRepository users;
    private final StoreRepository storeRepository;
    private final StoreService stores;
    private final StockEvents events;

    public ItemService(ItemRepository items, UserRepository users, StoreRepository storeRepository,
                       StoreService stores, StockEvents events) {
        this.items = items;
        this.users = users;
        this.storeRepository = storeRepository;
        this.stores = stores;
        this.events = events;
    }

    // ---- public ----

    @Transactional(readOnly = true)
    public PageResponse<ItemResponse> browse(String query, int page, int size) {
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        return PageResponse.from(
                items.browse(ItemStatus.AVAILABLE, Instant.now(), likePattern(query), pageRequest),
                ItemResponse::from);
    }

    @Transactional(readOnly = true)
    public ItemResponse get(UUID itemId) {
        return ItemResponse.from(findVisible(itemId));
    }

    // ---- store only (the user id always comes from the login token; the store is looked up from it) ----

    @Transactional(readOnly = true)
    public List<ItemResponse> listMine(UUID userId) {
        return items.findByStoreIdAndStatusNotOrderByExpiresAtAsc(stores.storeIdFor(userId), ItemStatus.REMOVED)
                .stream()
                .map(ItemResponse::from)
                .toList();
    }

    @Transactional
    public ItemResponse create(UUID userId, ItemRequest request) {
        if (request.quantity() < 1) {
            throw new BadRequestException("quantity must be at least 1 for a new listing");
        }
        checkBusinessRules(request);
        UUID storeId = stores.storeIdFor(userId);
        Item item = items.saveAndFlush(new Item(
                storeRepository.getReferenceById(storeId), users.getReferenceById(userId),
                request.name().trim(), request.description(),
                request.price(), request.originalPrice(), request.quantity(),
                request.expiresAt(), request.pickupLocation().trim(),
                request.pickupStart(), request.pickupEnd()));
        events.record(item.getId(), StockEventType.LISTED, item.getQuantity(), null, userId);
        return ItemResponse.from(item);
    }

    @Transactional
    public ItemResponse update(UUID userId, UUID itemId, ItemRequest request) {
        if (request.version() == null) {
            throw new BadRequestException("version is required when updating an item");
        }
        Item item = findOwned(userId, itemId);
        if (item.getVersion() != request.version()) {
            // Stock or details changed since the owner loaded the form (e.g. a buyer just reserved some).
            throw new ConflictException("This item changed since you loaded it. Reload and try again.");
        }
        checkBusinessRules(request);
        int before = item.getQuantity();
        item.update(request.name().trim(), request.description(),
                request.price(), request.originalPrice(), request.quantity(),
                request.expiresAt(), request.pickupLocation().trim(),
                request.pickupStart(), request.pickupEnd());
        items.flush();   // write now, so the response shows the new version number (and the event sees the new stock)
        if (item.getQuantity() != before) {
            events.record(item.getId(), StockEventType.ADJUSTED, item.getQuantity() - before, null, userId);
        }
        return ItemResponse.from(item);
    }

    @Transactional
    public void remove(UUID userId, UUID itemId) {
        Item item = findOwned(userId, itemId);
        item.remove();
        items.flush();
        events.record(item.getId(), StockEventType.REMOVED, 0, null, userId);
    }

    // ---- helpers ----

    private Item findVisible(UUID itemId) {
        return items.findWithStoreById(itemId)
                .filter(item -> !item.isRemoved())
                .orElseThrow(() -> new NotFoundException("Item not found"));
    }

    private Item findOwned(UUID userId, UUID itemId) {
        UUID storeId = stores.storeIdFor(userId);
        Item item = findVisible(itemId);
        if (!item.belongsTo(storeId)) {
            throw new ForbiddenException("You can only change your own store's listings");
        }
        return item;
    }

    /** Rules that span several fields. The database has matching CHECK constraints as a backstop. */
    private static void checkBusinessRules(ItemRequest request) {
        if (request.price().compareTo(request.originalPrice()) > 0) {
            throw new BadRequestException("price can't be higher than the original price");
        }
        if (!request.pickupStart().isBefore(request.pickupEnd())) {
            throw new BadRequestException("pickupStart must be before pickupEnd");
        }
        if (request.pickupEnd().isAfter(request.expiresAt())) {
            throw new BadRequestException("pickup must end before the food expires");
        }
        if (!request.expiresAt().isAfter(Instant.now())) {
            throw new BadRequestException("expiresAt must be in the future");
        }
    }

    /**
     * Turns a search box value into a safe LIKE pattern. '%' and '_' typed by
     * a user are escaped, so "50%" searches for the text "50%" instead of acting as a wildcard.
     */
    static String likePattern(String query) {
        if (query == null || query.isBlank()) {
            return "%";
        }
        String escaped = query.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }
}
