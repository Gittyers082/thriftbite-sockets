package com.thriftbite.backend.account;

import com.thriftbite.backend.account.dto.AccountExport;
import com.thriftbite.backend.account.dto.AccountResponse;
import com.thriftbite.backend.account.dto.AccountResponse.BuyerSummary;
import com.thriftbite.backend.account.dto.AccountResponse.Missed;
import com.thriftbite.backend.account.dto.AccountResponse.Month;
import com.thriftbite.backend.auth.TokenService;
import com.thriftbite.backend.auth.dto.LoginResponse;
import com.thriftbite.backend.auth.dto.UserResponse;
import com.thriftbite.backend.cart.CartItemRepository;
import com.thriftbite.backend.common.ApiExceptions.BadRequestException;
import com.thriftbite.backend.common.ApiExceptions.ForbiddenException;
import com.thriftbite.backend.common.ApiExceptions.NotFoundException;
import com.thriftbite.backend.common.ApiExceptions.TooManyRequestsException;
import com.thriftbite.backend.common.ratelimit.LoginAttempts;
import com.thriftbite.backend.follow.FollowService;
import com.thriftbite.backend.item.ItemService;
import com.thriftbite.backend.reservation.ReservationService;
import com.thriftbite.backend.reservation.ReservationStatus;
import com.thriftbite.backend.reservation.dto.ReservationResponse;
import com.thriftbite.backend.store.StoreProfileService;
import com.thriftbite.backend.user.Role;
import com.thriftbite.backend.user.User;
import com.thriftbite.backend.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** What a person can do with their own account. The account is always the one in the login token. */
@Service
public class AccountService {

    private static final String ZONE = "Asia/Manila";

    private final UserRepository users;
    private final RecoveryCodes recoveryCodes;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttempts attempts;
    private final TokenService tokens;
    private final ReservationService reservations;
    private final CartItemRepository cart;
    private final ItemService items;
    private final StoreProfileService storeProfiles;
    private final FollowService follows;
    private final JdbcTemplate jdbc;
    private final int maxActiveReservations;

    public AccountService(UserRepository users, RecoveryCodes recoveryCodes, PasswordEncoder passwordEncoder,
                          LoginAttempts attempts, TokenService tokens, ReservationService reservations,
                          CartItemRepository cart, ItemService items, StoreProfileService storeProfiles,
                          FollowService follows, JdbcTemplate jdbc,
                          @Value("${app.reservations.max-active-per-buyer:2}") int maxActiveReservations) {
        this.users = users;
        this.recoveryCodes = recoveryCodes;
        this.passwordEncoder = passwordEncoder;
        this.attempts = attempts;
        this.tokens = tokens;
        this.reservations = reservations;
        this.cart = cart;
        this.items = items;
        this.storeProfiles = storeProfiles;
        this.follows = follows;
        this.jdbc = jdbc;
        this.maxActiveReservations = maxActiveReservations;
    }

    @Transactional(readOnly = true)
    public AccountResponse summary(UUID userId) {
        User user = account(userId);
        RecoveryCodes.Status codes = recoveryCodes.status(userId);
        return new AccountResponse(user.getUsername(), user.getRole(), user.getCreatedAt(), user.isDemo(),
                codes.left(), codes.madeAt(), user.getRole() == Role.CONSUMER ? buyerSummary(userId) : null);
    }

    /**
     * Changes the password and logs out every other device.
     *
     * @return a fresh login for this device, since its old token just stopped working too
     */
    @Transactional
    public LoginResponse changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = account(userId);
        confirmPassword(user, currentPassword);
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BadRequestException("That's the password you already have. Choose a different one.");
        }
        user.changePassword(passwordEncoder.encode(newPassword));
        users.saveAndFlush(user);   // so the new token carries the new version
        TokenService.IssuedToken token = tokens.issue(user);
        return new LoginResponse(token.value(), token.expiresAt(), UserResponse.from(user));
    }

    /** Replaces the recovery codes. The old ones stop working. */
    @Transactional
    public List<String> newRecoveryCodes(UUID userId, String password) {
        confirmPassword(account(userId), password);
        return recoveryCodes.replace(userId);
    }

    /** Everything kept about this account, for "Download my data". */
    @Transactional(readOnly = true)
    public AccountExport export(UUID userId) {
        AccountResponse account = summary(userId);
        return switch (account.role()) {
            case CONSUMER -> new AccountExport(Instant.now(), account, reservations.listForBuyer(userId),
                    follows.followed(userId).stream().map(FollowService.Followed::name).toList(), null, null);
            case OWNER -> new AccountExport(Instant.now(), account, null, null,
                    storeProfiles.mine(userId), items.listMine(userId));
            case ADMIN -> new AccountExport(Instant.now(), account, null, null, null, null);
        };
    }

    /**
     * Deletes a buyer's account. Anything they're holding goes back on sale, and the account
     * row is scrubbed rather than removed: stores' past reservations stay, without the username.
     *
     * Store accounts can't do this themselves, because other people's reservations hang off the store.
     */
    @Transactional
    public void delete(UUID userId, String password) {
        User user = account(userId);
        if (user.getRole() != Role.CONSUMER) {
            throw new ForbiddenException("Store and admin accounts can't be deleted here. Ask an admin to close the account.");
        }
        confirmPassword(user, password);

        reservations.listForBuyer(userId).stream()
                .filter(reservation -> reservation.status() == ReservationStatus.RESERVED)
                .map(ReservationResponse::id)
                .forEach(reservationId -> reservations.cancel(userId, reservationId));
        cart.deleteAllForUser(userId);
        recoveryCodes.deleteAll(userId);
        follows.forget(userId);   // who they followed, and their alerts

        // cancel() cleared Hibernate's session, so load the account again before changing it.
        User fresh = account(userId);
        String placeholder;
        do {
            placeholder = "deleted-" + UUID.randomUUID().toString().substring(0, 12);
        } while (users.existsByUsername(placeholder));
        // A random password nobody knows: the hash can never match anything typed.
        fresh.scrub(placeholder, passwordEncoder.encode(UUID.randomUUID().toString()), Instant.now());
        users.saveAndFlush(fresh);
    }

    // ---- helpers ----

    private User account(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("User no longer exists"));
    }

    /**
     * Asks for the password again before a sensitive change. Wrong tries are counted per
     * account, so a stolen login can't be used to guess the password quickly.
     */
    private void confirmPassword(User user, String password) {
        if (user.isDemo()) {
            throw new ForbiddenException("The demo accounts are shared, so their sign-in settings can't be changed. "
                    + "Sign up for your own account to try this.");
        }
        String key = "password|" + user.getId();
        Duration wait = attempts.blockedFor(key);
        if (wait != null) {
            throw new TooManyRequestsException(
                    "Too many wrong passwords. Wait a few minutes and try again.", wait, "password_locked");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            int left = attempts.recordFailure(key);
            throw new BadRequestException("That isn't your current password. " + (left == 0
                    ? "Wait a few minutes before trying again."
                    : left + (left == 1 ? " try" : " tries") + " left."));
        }
        attempts.reset(key);
    }

    private BuyerSummary buyerSummary(UUID buyerId) {
        // "Saved" is the usual price minus the price paid, on food that was actually picked up.
        String saved = "COALESCE(SUM((ri.original_unit_price - ri.unit_price) * ri.quantity), 0)";
        BuyerSummary totals = jdbc.queryForObject("""
                SELECT %s AS saved, COALESCE(SUM(ri.quantity), 0) AS meals,
                       (SELECT COUNT(*) FROM reservations a WHERE a.buyer_id = ? AND a.status = 'RESERVED') AS active
                  FROM reservations r JOIN reservation_items ri ON ri.reservation_id = r.id
                 WHERE r.buyer_id = ? AND r.status = 'COMPLETED'
                """.formatted(saved),
                (rs, row) -> new BuyerSummary(rs.getBigDecimal("saved"), rs.getInt("meals"), List.of(), List.of(),
                        rs.getLong("active"), maxActiveReservations),
                buyerId, buyerId);

        List<Month> months = jdbc.query("""
                SELECT to_char(m.month, 'YYYY-MM') AS month, %s AS saved
                  FROM generate_series(date_trunc('month', now() AT TIME ZONE ?) - interval '2 months',
                                       date_trunc('month', now() AT TIME ZONE ?), interval '1 month') AS m(month)
                  LEFT JOIN reservations r ON r.buyer_id = ? AND r.status = 'COMPLETED'
                        AND date_trunc('month', r.completed_at AT TIME ZONE ?) = m.month
                  LEFT JOIN reservation_items ri ON ri.reservation_id = r.id
                 GROUP BY m.month ORDER BY m.month
                """.formatted(saved),
                (rs, row) -> new Month(rs.getString("month"), rs.getBigDecimal("saved")),
                ZONE, ZONE, buyerId, ZONE);

        List<Missed> missed = jdbc.query("""
                SELECT s.name, (COALESCE(r.expired_at, r.pickup_deadline) AT TIME ZONE ?)::date AS day
                  FROM reservations r JOIN stores s ON s.id = r.store_id
                 WHERE r.buyer_id = ? AND r.status = 'EXPIRED'
                   AND COALESCE(r.expired_at, r.pickup_deadline) >= now() - interval '30 days'
                 ORDER BY COALESCE(r.expired_at, r.pickup_deadline) DESC
                 LIMIT 20
                """,
                (rs, row) -> new Missed(rs.getString("name"), rs.getObject("day", LocalDate.class)),
                ZONE, buyerId);

        return new BuyerSummary(totals.saved(), totals.mealsSaved(), months, missed,
                totals.activeReservations(), totals.maxActiveReservations());
    }
}
