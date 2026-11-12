package com.thriftbite.backend.storefront;

import com.thriftbite.backend.item.dto.ItemResponse;
import com.thriftbite.backend.store.dto.StoreResponse;

import java.time.LocalTime;
import java.util.List;

/**
 * A store's public page: its profile, what it has for pickup now, and a few numbers.
 *
 * @param items food on sale or just sold out, not yet expired
 */
public record StorePageResponse(StoreResponse store, List<ItemResponse> items, Stats stats) {

    /**
     * All counted over the last 30 days.
     *
     * @param mealsRescued  units of food picked up
     * @param pickupRate    share of reservations that were picked up rather than missed (0.98 = 98%);
     *                      null until there are enough to mean something
     * @param usualPostTime the time of day the store usually posts food (Manila); null until it has posted a few times
     */
    public record Stats(int mealsRescued, Double pickupRate, LocalTime usualPostTime) {
    }
}
