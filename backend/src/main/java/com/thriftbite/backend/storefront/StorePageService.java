package com.thriftbite.backend.storefront;

import com.thriftbite.backend.common.ApiExceptions.NotFoundException;
import com.thriftbite.backend.item.ItemRepository;
import com.thriftbite.backend.item.ItemStatus;
import com.thriftbite.backend.item.dto.ItemResponse;
import com.thriftbite.backend.store.Store;
import com.thriftbite.backend.store.StoreMemberRepository;
import com.thriftbite.backend.store.StoreRepository;
import com.thriftbite.backend.store.dto.StoreResponse;
import com.thriftbite.backend.storefront.StorePageResponse.Stats;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Puts together a store's public page. */
@Service
public class StorePageService {

    /** Below this many finished reservations, a percentage says more about luck than about the store. */
    private static final int MIN_RESERVATIONS_FOR_RATE = 5;
    private static final int MIN_POSTS_FOR_USUAL_TIME = 3;

    private final StoreRepository stores;
    private final StoreMemberRepository members;
    private final ItemRepository items;
    private final JdbcTemplate jdbc;

    public StorePageService(StoreRepository stores, StoreMemberRepository members, ItemRepository items,
                            JdbcTemplate jdbc) {
        this.stores = stores;
        this.members = members;
        this.items = items;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public StorePageResponse page(String slug) {
        // A store that signed up but hasn't been approved has no public page yet.
        Store store = stores.findBySlug(slug.toLowerCase(Locale.ROOT))
                .filter(found -> members.hasApprovedMember(found.getId()))
                .orElseThrow(() -> new NotFoundException("Store not found"));
        List<ItemResponse> onSale = items.findByStoreIdAndStatusInAndExpiresAtAfterOrderByStatusAscExpiresAtAsc(
                        store.getId(), List.of(ItemStatus.AVAILABLE, ItemStatus.SOLD_OUT), Instant.now())
                .stream().map(ItemResponse::from).toList();
        return new StorePageResponse(StoreResponse.from(store), onSale, stats(store.getId()));
    }

    /** One query. These are the same kind of numbers a regular customer could count for themselves. */
    private Stats stats(UUID storeId) {
        return jdbc.queryForObject("""
                WITH closed AS (
                    SELECT status FROM reservations
                     WHERE store_id = ? AND status IN ('COMPLETED', 'EXPIRED')
                       AND created_at >= now() - interval '30 days'
                ), posts AS (
                    SELECT EXTRACT(EPOCH FROM (occurred_at AT TIME ZONE 'Asia/Manila')::time) AS seconds
                      FROM item_events
                     WHERE store_id = ? AND type = 'LISTED' AND occurred_at >= now() - interval '30 days'
                )
                SELECT (SELECT COALESCE(SUM(ri.quantity), 0)
                          FROM reservation_items ri JOIN reservations r ON r.id = ri.reservation_id
                         WHERE r.store_id = ? AND r.status = 'COMPLETED'
                           AND r.completed_at >= now() - interval '30 days')            AS meals,
                       (SELECT COUNT(*) FROM closed WHERE status = 'COMPLETED')        AS picked_up,
                       (SELECT COUNT(*) FROM closed)                                   AS closed,
                       (SELECT COUNT(*) FROM posts)                                    AS posts,
                       (SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY seconds) FROM posts) AS usual_post_seconds
                """,
                (rs, row) -> {
                    int closed = rs.getInt("closed");
                    Double pickupRate = closed >= MIN_RESERVATIONS_FOR_RATE
                            ? (double) rs.getInt("picked_up") / closed : null;
                    LocalTime usualPostTime = rs.getInt("posts") >= MIN_POSTS_FOR_USUAL_TIME
                            ? toNearestHalfHour(rs.getDouble("usual_post_seconds")) : null;
                    return new Stats(rs.getInt("meals"), pickupRate, usualPostTime);
                },
                storeId, storeId, storeId);
    }

    /** "Posts around 3:30 PM" reads better, and promises less, than "3:27 PM". */
    static LocalTime toNearestHalfHour(double secondsOfDay) {
        long halfHours = Math.round(secondsOfDay / 1800.0);
        return LocalTime.ofSecondOfDay(Math.min(halfHours, 47) * 1800);
    }
}
