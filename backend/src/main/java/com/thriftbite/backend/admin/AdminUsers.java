package com.thriftbite.backend.admin;

import com.thriftbite.backend.admin.dto.AdminUser;
import com.thriftbite.backend.common.ApiExceptions.BadRequestException;
import com.thriftbite.backend.common.ApiExceptions.ConflictException;
import com.thriftbite.backend.common.ApiExceptions.ForbiddenException;
import com.thriftbite.backend.common.ApiExceptions.NotFoundException;
import com.thriftbite.backend.demo.DemoData;
import com.thriftbite.backend.item.ItemService;
import com.thriftbite.backend.live.LiveChange.DealsChanged;
import com.thriftbite.backend.reservation.ReservationService;
import com.thriftbite.backend.user.Role;
import com.thriftbite.backend.user.User;
import com.thriftbite.backend.user.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * The admins' list of accounts, and suspending one.
 *
 * Suspending locks the account out at once. A suspended store's listings come down and
 * the pickups waiting there are cancelled, with an alert to each buyer so nobody travels
 * for food that isn't there. A suspended buyer's reservations go back on sale.
 *
 * Who can't be suspended: admins (one admin locking out another isn't something to do
 * from a button), and, by the public demo's admin, anything but the demo's sample
 * accounts. The three accounts every demo visitor logs in with are off limits to it too:
 * one visitor shouldn't be able to break the demo for the next.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminUsers {

    private static final Set<String> SHARED_DEMO_LOGINS = Set.of(DemoData.BUYER, DemoData.OWNER, DemoData.ADMIN);

    private static final String SELECT = """
            SELECT u.id, u.username, u.role, u.approved, u.demo, u.created_at, u.suspended_at, u.suspended_reason,
                   s.name AS store_name, s.slug AS store_slug,
                   (SELECT COUNT(*) FROM reservations r
                     WHERE r.buyer_id = u.id AND r.status = 'EXPIRED' AND NOT r.miss_excused
                       AND r.expired_at > now() - interval '30 days') AS missed,
                   (SELECT COUNT(*) FROM reports x
                     WHERE x.reporter_id = u.id AND x.created_at > now() - interval '90 days') AS reports_sent,
                   (SELECT COUNT(*) FROM reports x
                     WHERE x.store_id = s.id AND x.created_at > now() - interval '90 days') AS reports_about
              FROM users u
              LEFT JOIN store_members m ON m.user_id = u.id
              LEFT JOIN stores s ON s.id = m.store_id
             WHERE u.deleted_at IS NULL AND u.role <> 'ADMIN' AND (u.demo OR NOT ?)
            """;

    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final ReservationService reservations;
    private final ItemService items;
    private final AdminLog log;
    private final ApplicationEventPublisher live;

    AdminUsers(JdbcTemplate jdbc, UserRepository users, ReservationService reservations, ItemService items,
               AdminLog log, ApplicationEventPublisher live) {
        this.jdbc = jdbc;
        this.users = users;
        this.reservations = reservations;
        this.items = items;
        this.log = log;
        this.live = live;
    }

    /**
     * Buyers and stores, suspended ones first, then newest. Admins aren't listed, and
     * deleted accounts have nothing left to show.
     *
     * @param search part of a username or store name; blank for everyone
     * @param show   "buyers", "stores", "suspended", or anything else for all
     */
    @Transactional(readOnly = true)
    public List<AdminUser> list(User admin, String search, String show) {
        String like = "%" + escapeLike(search == null ? "" : search.trim().toLowerCase(Locale.ROOT)) + "%";
        String only = switch (show == null ? "" : show) {
            case "buyers" -> " AND u.role = 'CONSUMER'";
            case "stores" -> " AND u.role = 'OWNER'";
            case "suspended" -> " AND u.suspended_at IS NOT NULL";
            default -> "";
        };
        return jdbc.query(SELECT + only + """
                 AND (lower(u.username) LIKE ? ESCAPE '\\' OR lower(COALESCE(s.name, '')) LIKE ? ESCAPE '\\')
                 ORDER BY (u.suspended_at IS NOT NULL) DESC, u.created_at DESC
                 LIMIT 100
                """, row(admin), admin.isDemo(), like, like);
    }

    @Transactional
    public AdminUser suspend(User admin, UUID userId, String reason) {
        User target = target(admin, userId);
        if (target.isSuspended()) {
            throw new ConflictException("This account is already suspended");
        }
        String why = reason.trim();
        target.suspend(why, Instant.now());
        users.saveAndFlush(target);

        String outcome;
        if (target.getRole() == Role.OWNER) {
            UUID storeId = storeOf(target);
            int cancelled = storeId == null ? 0 : reservations.cancelAllAtStore(storeId, admin.getId());
            int removed = storeId == null ? 0 : items.removeAllFor(storeId, admin.getId());
            live.publishEvent(new DealsChanged());   // browse pages drop the store's food
            outcome = count(removed, "listing") + " taken down, " + count(cancelled, "pickup") + " cancelled";
        } else {
            outcome = count(reservations.cancelAllOfBuyer(target.getId(), admin.getId()), "reservation") + " cancelled";
        }
        log.record(admin, "SUSPEND_ACCOUNT", target.getUsername(), why + " (" + outcome + ")");
        return one(admin, userId);
    }

    @Transactional
    public AdminUser liftSuspension(User admin, UUID userId) {
        User target = target(admin, userId);
        if (!target.isSuspended()) {
            throw new ConflictException("This account isn't suspended");
        }
        target.liftSuspension();
        users.saveAndFlush(target);
        log.record(admin, "LIFT_SUSPENSION", target.getUsername(), null);
        return one(admin, userId);
    }

    /** The account, if this admin may suspend it or lift its suspension. */
    private User target(User admin, UUID userId) {
        User target = users.findById(userId)
                .filter(user -> user.getDeletedAt() == null)
                .filter(user -> user.isDemo() || !admin.isDemo())   // real accounts don't exist for the demo admin
                .orElseThrow(() -> new NotFoundException("Account not found"));
        if (target.getRole() == Role.ADMIN) {
            throw new ForbiddenException("Admin accounts can't be suspended");
        }
        if (admin.isDemo() && SHARED_DEMO_LOGINS.contains(target.getUsername())) {
            throw new ForbiddenException("This is one of the accounts every demo visitor logs in with, so the demo admin "
                    + "can't suspend it. Try it on one of the other sample accounts.");
        }
        return target;
    }

    private AdminUser one(User admin, UUID userId) {
        List<AdminUser> found = jdbc.query(SELECT + " AND u.id = ?", row(admin), admin.isDemo(), userId);
        if (found.isEmpty()) {
            throw new BadRequestException("Account not found");
        }
        return found.get(0);
    }

    private UUID storeOf(User owner) {
        List<UUID> stores = jdbc.queryForList("SELECT store_id FROM store_members WHERE user_id = ?", UUID.class, owner.getId());
        return stores.isEmpty() ? null : stores.get(0);
    }

    private static RowMapper<AdminUser> row(User admin) {
        return (rs, n) -> {
            Timestamp suspended = rs.getTimestamp("suspended_at");
            String username = rs.getString("username");
            return new AdminUser(rs.getObject("id", UUID.class), username, Role.valueOf(rs.getString("role")),
                    rs.getBoolean("approved"), rs.getTimestamp("created_at").toInstant(),
                    rs.getString("store_name"), rs.getString("store_slug"),
                    suspended == null ? null : suspended.toInstant(), rs.getString("suspended_reason"),
                    rs.getInt("missed"), rs.getInt("reports_sent"), rs.getInt("reports_about"),
                    !(admin.isDemo() && SHARED_DEMO_LOGINS.contains(username)));
        };
    }

    private static String count(int n, String thing) {
        return n + " " + thing + (n == 1 ? "" : "s");
    }

    /** So a search for "50%" or "a_b" looks for those characters, not for "anything". */
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
