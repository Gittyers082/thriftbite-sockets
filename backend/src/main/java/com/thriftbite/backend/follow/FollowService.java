package com.thriftbite.backend.follow;

import com.thriftbite.backend.common.ApiExceptions.ConflictException;
import com.thriftbite.backend.common.ApiExceptions.NotFoundException;
import com.thriftbite.backend.store.Store;
import com.thriftbite.backend.store.StoreMemberRepository;
import com.thriftbite.backend.store.StoreRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Buyers following stores. A follower with alerts on is told when the store posts food. */
@Service
public class FollowService {

    /** Far more than anyone follows by hand; stops a script from filling the table. */
    private static final int MAX_FOLLOWED = 100;

    private final StoreRepository stores;
    private final StoreMemberRepository members;
    private final JdbcTemplate jdbc;

    public FollowService(StoreRepository stores, StoreMemberRepository members, JdbcTemplate jdbc) {
        this.stores = stores;
        this.members = members;
        this.jdbc = jdbc;
    }

    /**
     * @param area          the store's barangay, or its street address if it hasn't given one
     * @param usualPostTime when it usually posts (Manila), to the nearest half hour; null until it has posted a few times
     */
    public record Followed(UUID storeId, String slug, String name, String area, LocalTime usualPostTime, boolean alertsOn) {
    }

    public record FollowState(boolean following, boolean alertsOn) {
    }

    /** Follows the store, or changes whether its alerts are on. Doing it twice changes nothing. */
    @Transactional
    public FollowState follow(UUID userId, String slug, boolean alertsOn) {
        Store store = publicStore(slug);
        Integer followed = jdbc.queryForObject(
                "SELECT COUNT(*) FROM store_follows WHERE user_id = ? AND store_id <> ?", Integer.class, userId, store.getId());
        if (followed != null && followed >= MAX_FOLLOWED) {
            throw new ConflictException("You follow " + MAX_FOLLOWED + " stores, which is the most you can. Unfollow one first.");
        }
        jdbc.update("""
                INSERT INTO store_follows (user_id, store_id, alerts_on) VALUES (?, ?, ?)
                ON CONFLICT (user_id, store_id) DO UPDATE SET alerts_on = EXCLUDED.alerts_on
                """, userId, store.getId(), alertsOn);
        return new FollowState(true, alertsOn);
    }

    @Transactional
    public void unfollow(UUID userId, String slug) {
        jdbc.update("DELETE FROM store_follows WHERE user_id = ? AND store_id = ?", userId, publicStore(slug).getId());
    }

    /** Null if the buyer doesn't follow the store. */
    public FollowState state(UUID userId, UUID storeId) {
        return jdbc.query("SELECT alerts_on FROM store_follows WHERE user_id = ? AND store_id = ?",
                rs -> rs.next() ? new FollowState(true, rs.getBoolean("alerts_on")) : null, userId, storeId);
    }

    public int followers(UUID storeId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM store_follows WHERE store_id = ?", Integer.class, storeId);
        return count == null ? 0 : count;
    }

    @Transactional(readOnly = true)
    public List<Followed> followed(UUID userId) {
        return jdbc.query("""
                SELECT s.id, s.slug, s.name, COALESCE(s.barangay, s.address) AS area, f.alerts_on, p.posts, p.usual_seconds
                  FROM store_follows f
                  JOIN stores s ON s.id = f.store_id
                  LEFT JOIN LATERAL (
                      SELECT COUNT(*) AS posts,
                             percentile_cont(0.5) WITHIN GROUP (
                                 ORDER BY EXTRACT(EPOCH FROM (e.occurred_at AT TIME ZONE 'Asia/Manila')::time)) AS usual_seconds
                        FROM item_events e
                       WHERE e.store_id = s.id AND e.type = 'LISTED' AND e.occurred_at >= now() - interval '30 days'
                  ) p ON TRUE
                 WHERE f.user_id = ?
                 ORDER BY lower(s.name)
                """,
                (rs, row) -> new Followed(rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("name"),
                        rs.getString("area"),
                        rs.getInt("posts") >= 3 ? nearestHalfHour(rs.getDouble("usual_seconds")) : null,
                        rs.getBoolean("alerts_on")),
                userId);
    }

    @Transactional
    public void forget(UUID userId) {
        jdbc.update("DELETE FROM store_follows WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM alerts WHERE user_id = ?", userId);
    }

    /** Stores that aren't approved yet have no page, so there's nothing to follow. */
    private Store publicStore(String slug) {
        return stores.findBySlug(slug.toLowerCase(Locale.ROOT))
                .filter(store -> members.hasApprovedMember(store.getId()))
                .orElseThrow(() -> new NotFoundException("Store not found"));
    }

    private static LocalTime nearestHalfHour(double secondsOfDay) {
        return LocalTime.ofSecondOfDay(Math.min(Math.round(secondsOfDay / 1800.0), 47) * 1800);
    }
}
