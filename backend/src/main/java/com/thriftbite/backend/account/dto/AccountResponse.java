package com.thriftbite.backend.account.dto;

import com.thriftbite.backend.user.Role;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The account page.
 *
 * @param demo                 a shared demo account: it can't change its sign-in settings
 * @param recoveryCodesLeft    unused recovery codes
 * @param recoveryCodesMadeAt  when the current set was made; null if the account has none
 * @param buyer                savings and pickup record; null for store and admin accounts
 */
public record AccountResponse(
        String username,
        Role role,
        Instant memberSince,
        boolean demo,
        int recoveryCodesLeft,
        Instant recoveryCodesMadeAt,
        BuyerSummary buyer
) {

    /**
     * @param saved          usual price minus price paid, over everything picked up
     * @param mealsSaved     units picked up
     * @param months         the last three calendar months (Manila), oldest first
     * @param missedPickups  reservations not picked up in the last 30 days, newest first (excused ones left out)
     * @param reserveBlockedUntil  when reserving is allowed again after too many missed pickups; null if it is now
     * @param missesBeforeBlock    the rule: this many misses within {@code missWindowDays} pauses reserving for {@code blockDays}
     */
    public record BuyerSummary(
            BigDecimal saved,
            int mealsSaved,
            List<Month> months,
            List<Missed> missedPickups,
            long activeReservations,
            int maxActiveReservations,
            Instant reserveBlockedUntil,
            int missesBeforeBlock,
            int missWindowDays,
            int blockDays
    ) {
    }

    /** @param month "2026-11" */
    public record Month(String month, BigDecimal saved) {
    }

    public record Missed(String storeName, LocalDate day) {
    }
}
