package com.thriftbite.backend.item.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Something the store has listed before, with how it went last time, for the
 * "Post again" screen.
 *
 * @param lastDay           the day it was last listed (the same weekday as the day being posted for, when there is one)
 * @param sameWeekday       whether {@code lastDay} is that same weekday
 * @param lastListed        how many were listed that day
 * @param lastSold          how many of those were picked up
 * @param lastSoldOutAt     when it sold out that day (Manila time), or null if it didn't
 * @param suggestedQuantity a different amount worth trying, or null when last time's amount looks right
 * @param alreadyListed     true if it's already on sale for the day being posted for
 */
public record UsualListing(
        String name,
        String description,
        BigDecimal price,
        BigDecimal originalPrice,
        String pickupLocation,
        LocalDate lastDay,
        boolean sameWeekday,
        int lastListed,
        int lastSold,
        LocalTime lastSoldOutAt,
        Integer suggestedQuantity,
        boolean alreadyListed
) {
}
