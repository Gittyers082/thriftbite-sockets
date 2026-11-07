package com.thriftbite.backend.stats;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Everything the owner's Sales screen shows for one period. Days are calendar
 * days in {@code timeZone} (Asia/Manila), not UTC. Ratios are 0 to 1, or null
 * when there's nothing to divide by.
 */
public record SalesStats(
        String storeName,
        LocalDate from,
        LocalDate to,
        String timeZone,
        Totals totals,
        List<Day> days,
        List<WeekdayLeftovers> leftoverByWeekday,
        List<HourPickups> pickupsByHour,
        List<ItemStats> items
) {

    /**
     * @param moneyMade          paid at the counter for pickups handed over in the period
     * @param moneyMadeChange    vs the same number of days just before (0.12 = up 12%), null if that was 0
     * @param discountGiven      (usual price - sale price) x quantity, on what was handed over
     * @param averageDiscount    discountGiven / (moneyMade + discountGiven)
     * @param unitsListed        stock put up for food listed in the period (LISTED + ADJUSTED)
     * @param unitsSoldOfListed  how much of that food was handed over
     * @param unitsLeftOver      how much of it was still unsold when it expired or was taken down
     * @param sellThrough        unitsSoldOfListed / unitsListed
     * @param missedRate         missed / (completed + missed), for reservations made in the period
     * @param missedValue        what the missed reservations were worth (held, then released)
     */
    public record Totals(
            BigDecimal moneyMade,
            BigDecimal moneyMadePreviousPeriod,
            Double moneyMadeChange,
            int unitsSold,
            int pickups,
            BigDecimal discountGiven,
            Double averageDiscount,
            int unitsListed,
            int unitsSoldOfListed,
            int unitsLeftOver,
            Double sellThrough,
            int completedReservations,
            int missedPickups,
            int cancelledReservations,
            Double missedRate,
            BigDecimal missedValue) {
    }

    public record Day(LocalDate date, DayOfWeek weekday, BigDecimal moneyMade, int unitsSold, int pickups) {
    }

    /** @param days how many of this weekday the period has; averagePerDay is null if none */
    public record WeekdayLeftovers(DayOfWeek weekday, int days, int unitsLeftOver, Double averagePerDay) {
    }

    public record HourPickups(int hour, int pickups) {
    }

    /**
     * One food, across all the days it was listed in the period ("Pandesal, bag of 10" every day).
     *
     * @param usualSellOutTime median time of day it sold out, on the days it did; null if it never did
     */
    public record ItemStats(
            String name,
            int listed,
            int sold,
            int leftOver,
            Double sellThrough,
            BigDecimal moneyMade,
            int daysListed,
            int daysSoldOut,
            LocalTime usualSellOutTime) {
    }
}
