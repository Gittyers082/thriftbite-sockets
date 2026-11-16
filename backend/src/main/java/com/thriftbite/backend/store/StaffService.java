package com.thriftbite.backend.store;

import com.thriftbite.backend.common.ApiExceptions.ConflictException;
import com.thriftbite.backend.common.ApiExceptions.ForbiddenException;
import com.thriftbite.backend.common.ApiExceptions.NotFoundException;
import com.thriftbite.backend.user.Role;
import com.thriftbite.backend.user.User;
import com.thriftbite.backend.user.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * A store's staff: accounts for the people who work the counter. Staff can do the daily
 * work (list food, hand over pickups) but not the owner's business: the store profile,
 * sales figures, other staff, or closing the store.
 *
 * The owner creates a staff account and is shown a temporary password once, to pass on.
 * There's no email or phone number in this app, so the owner is also how a staff member
 * gets back in: "new password" replaces a forgotten one.
 */
@Service
public class StaffService {

    static final int MAX_STAFF = 5;
    /** No 0/O or 1/I/L, so it survives being read out across a counter. */
    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";

    /** @param suspended an admin has suspended this account */
    public record Staff(UUID id, String username, Instant addedAt, boolean suspended) {
    }

    /** @param temporaryPassword shown this once; only its hash is stored */
    public record NewStaff(UUID id, String username, String temporaryPassword) {
    }

    private final StoreService stores;
    private final StoreRepository storeRepository;
    private final StoreMemberRepository members;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();

    StaffService(StoreService stores, StoreRepository storeRepository, StoreMemberRepository members,
                 UserRepository users, PasswordEncoder passwordEncoder, JdbcTemplate jdbc) {
        this.stores = stores;
        this.storeRepository = storeRepository;
        this.members = members;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<Staff> list(UUID ownerId) {
        return jdbc.query("""
                SELECT u.id, u.username, m.created_at, u.suspended_at IS NOT NULL AS suspended
                  FROM store_members m JOIN users u ON u.id = m.user_id
                 WHERE m.store_id = ? AND m.role = 'STAFF'
                 ORDER BY m.created_at
                """, (rs, row) -> new Staff(rs.getObject("id", UUID.class), rs.getString("username"),
                rs.getTimestamp("created_at").toInstant(), rs.getBoolean("suspended")),
                stores.ownedStoreIdFor(ownerId));
    }

    @Transactional
    public NewStaff add(UUID ownerId, String username) {
        UUID storeId = stores.ownedStoreIdFor(ownerId);
        refuseForDemo(ownerId);
        String name = username.trim().toLowerCase(Locale.ROOT);   // usernames are case-insensitive, as at sign-up
        if (users.existsByUsername(name)) {
            throw new ConflictException("Username is already taken");
        }
        Integer staff = jdbc.queryForObject("SELECT COUNT(*) FROM store_members WHERE store_id = ? AND role = 'STAFF'",
                Integer.class, storeId);
        if (staff != null && staff >= MAX_STAFF) {
            throw new ConflictException("A store can have " + MAX_STAFF + " staff accounts. Remove one to add another.");
        }
        String password = temporaryPassword();
        // A store account like the owner's, already approved: the owner vouches for their own staff.
        User user = new User(name, passwordEncoder.encode(password), Role.OWNER);
        user.approve();
        users.saveAndFlush(user);
        members.saveAndFlush(new StoreMember(storeRepository.getReferenceById(storeId), user, StoreRole.STAFF));
        return new NewStaff(user.getId(), user.getUsername(), password);
    }

    /** Replaces a staff member's password (they forgot it, or it leaked) and logs them out everywhere. */
    @Transactional
    public NewStaff newPassword(UUID ownerId, UUID staffId) {
        refuseForDemo(ownerId);
        User staff = staffOf(ownerId, staffId);
        String password = temporaryPassword();
        staff.changePassword(passwordEncoder.encode(password));
        users.saveAndFlush(staff);
        return new NewStaff(staff.getId(), staff.getUsername(), password);
    }

    /**
     * Removes a staff account. Like any deleted account the row is scrubbed, not dropped,
     * so listings they posted and pickups they handed over still add up.
     */
    @Transactional
    public void remove(UUID ownerId, UUID staffId) {
        refuseForDemo(ownerId);
        User staff = staffOf(ownerId, staffId);
        jdbc.update("DELETE FROM store_members WHERE user_id = ?", staff.getId());
        scrub(staff);
    }

    /** Makes an account unusable while keeping its row. Also used when a whole store closes. */
    public void scrub(User user) {
        String placeholder;
        do {
            placeholder = "deleted-" + UUID.randomUUID().toString().substring(0, 12);
        } while (users.existsByUsername(placeholder));
        // A random password nobody knows: the hash can never match anything typed.
        user.scrub(placeholder, passwordEncoder.encode(UUID.randomUUID().toString()), Instant.now());
        users.saveAndFlush(user);
        jdbc.update("DELETE FROM recovery_codes WHERE user_id = ?", user.getId());
    }

    /** Someone else's staff looks the same as nobody. */
    private User staffOf(UUID ownerId, UUID staffId) {
        UUID storeId = stores.ownedStoreIdFor(ownerId);
        Integer found = jdbc.queryForObject(
                "SELECT COUNT(*) FROM store_members WHERE store_id = ? AND user_id = ? AND role = 'STAFF'",
                Integer.class, storeId, staffId);
        if (found == null || found == 0) {
            throw new NotFoundException("Staff account not found");
        }
        return users.findById(staffId).orElseThrow(() -> new NotFoundException("Staff account not found"));
    }

    private void refuseForDemo(UUID ownerId) {
        if (users.findById(ownerId).map(User::isDemo).orElse(false)) {
            throw new ForbiddenException("The demo store is shared, so its staff can't be changed. "
                    + "Sign up for your own store to try this.");
        }
    }

    /** 12 characters as XXXX-XXXX-XXXX: about 59 bits, far more than anyone could guess before being locked out. */
    private String temporaryPassword() {
        StringBuilder password = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            if (i > 0 && i % 4 == 0) {
                password.append('-');
            }
            password.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return password.toString();
    }
}
