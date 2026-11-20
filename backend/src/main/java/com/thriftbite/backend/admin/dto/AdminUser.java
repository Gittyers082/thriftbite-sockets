package com.thriftbite.backend.admin.dto;

import com.thriftbite.backend.user.Role;

import java.time.Instant;
import java.util.UUID;

/**
 * An account as an admin sees it. Never the password or recovery codes.
 *
 * @param storeName       store accounts only
 * @param staff           a staff account at that store, not its owner
 * @param suspendedAt     null unless the account is suspended
 * @param missedPickups   buyers: pickups missed in the last 30 days
 * @param reportsSent     buyers: problem reports sent in the last 90 days
 * @param reportsAbout    stores: problem reports about the store in the last 90 days
 * @param canSuspend      false for the accounts this admin isn't allowed to suspend (see AdminUsers)
 */
public record AdminUser(
        UUID id,
        String username,
        Role role,
        boolean approved,
        Instant createdAt,
        String storeName,
        String storeSlug,
        boolean staff,
        Instant suspendedAt,
        String suspendedReason,
        int missedPickups,
        int reportsSent,
        int reportsAbout,
        boolean canSuspend
) {
}
