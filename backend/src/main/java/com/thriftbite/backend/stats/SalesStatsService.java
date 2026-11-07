package com.thriftbite.backend.stats;

import com.thriftbite.backend.common.ApiExceptions.BadRequestException;
import com.thriftbite.backend.store.StoreRepository;
import com.thriftbite.backend.store.StoreService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Turns the stock history and reservations into the owner's Sales screen. */
@Service
public class SalesStatsService {

    /** The business runs on Manila time; "Tuesday" means a Manila Tuesday. */
    public static final ZoneId ZONE = ZoneId.of("Asia/Manila");
    static final int MAX_DAYS = 92;

    private final SalesStatsQueries queries;
    private final StoreService stores;
    private final StoreRepository storeRepository;
    private final Clock clock = Clock.system(ZONE);

    public SalesStatsService(SalesStatsQueries queries, StoreService stores, StoreRepository storeRepository) {
        this.queries = queries;
        this.stores = stores;
        this.storeRepository = storeRepository;
    }

    /**
     * @param from first day, inclusive (default: 7 days before {@code to})
     * @param to   last day, inclusive (default: yesterday, so only full days count)
     */
    @Transactional(readOnly = true)
    public SalesStats forOwner(UUID userId, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock);
        LocalDate end = to != null ? to : today.minusDays(1);
        LocalDate start = from != null ? from : end.minusDays(6);
        if (end.isAfter(today)) {
            throw new BadRequestException("to can't be after today");
        }
        if (start.isAfter(end)) {
            throw new BadRequestException("from must be on or before to");
        }
        long length = ChronoUnit.DAYS.between(start, end) + 1;
        if (length > MAX_DAYS) {
            throw new BadRequestException("Pick at most " + MAX_DAYS + " days");
        }

        UUID storeId = stores.storeIdFor(userId);
        String storeName = storeRepository.findById(storeId).orElseThrow().getName();
        SalesStatsQueries.Period period = period(storeId, start, end);
        SalesStatsQueries.Period before = period(storeId, start.minusDays(length), start.minusDays(1));

        List<SalesStatsQueries.DayRow> days = queries.days(period);
        SalesStatsQueries.Outcomes outcomes = queries.outcomes(period);
        List<SalesStatsQueries.ItemRow> items = queries.items(period);
        BigDecimal previous = queries.moneyMade(before);

        BigDecimal money = days.stream().map(SalesStatsQueries.DayRow::money).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discount = days.stream().map(SalesStatsQueries.DayRow::discount).reduce(BigDecimal.ZERO, BigDecimal::add);
        int units = days.stream().mapToInt(SalesStatsQueries.DayRow::units).sum();
        int pickups = days.stream().mapToInt(SalesStatsQueries.DayRow::pickups).sum();
        int listed = items.stream().mapToInt(SalesStatsQueries.ItemRow::listed).sum();
        int soldOfListed = items.stream().mapToInt(SalesStatsQueries.ItemRow::sold).sum();
        int leftover = items.stream().mapToInt(SalesStatsQueries.ItemRow::leftover).sum();

        SalesStats.Totals totals = new SalesStats.Totals(
                money, previous,
                previous.signum() == 0 ? null : ratio(money.subtract(previous), previous),
                units, pickups, discount,
                ratio(discount, money.add(discount)),
                listed, soldOfListed, leftover, ratio(soldOfListed, listed),
                outcomes.completed(), outcomes.missed(), outcomes.cancelled(),
                ratio(outcomes.missed(), outcomes.completed() + outcomes.missed()),
                outcomes.missedValue());

        Map<Integer, Integer> byHour = queries.pickupsByHour(period).stream()
                .collect(Collectors.toMap(SalesStatsQueries.HourRow::hour, SalesStatsQueries.HourRow::pickups));

        return new SalesStats(
                storeName, start, end, ZONE.getId(), totals,
                days.stream().map(d -> new SalesStats.Day(d.date(), d.date().getDayOfWeek(), d.money(),
                        d.units(), d.pickups())).toList(),
                queries.leftoverByWeekday(period).stream().map(w -> new SalesStats.WeekdayLeftovers(
                        w.weekday(), w.days(), w.leftover(), w.days() == 0 ? null : round((double) w.leftover() / w.days())))
                        .toList(),
                IntStream.range(0, 24).mapToObj(h -> new SalesStats.HourPickups(h, byHour.getOrDefault(h, 0))).toList(),
                items.stream().map(i -> new SalesStats.ItemStats(i.name(), i.listed(), i.sold(), i.leftover(),
                        ratio(i.sold(), i.listed()), i.money(), i.daysListed(), i.daysSoldOut(), i.usualSellOutTime()))
                        .toList());
    }

    private static SalesStatsQueries.Period period(UUID storeId, LocalDate from, LocalDate to) {
        return new SalesStatsQueries.Period(storeId, from, to, ZONE.getId(),
                from.atStartOfDay(ZONE).toInstant(), to.plusDays(1).atStartOfDay(ZONE).toInstant());
    }

    private static Double ratio(int part, int whole) {
        return whole == 0 ? null : round((double) part / whole);
    }

    private static Double ratio(BigDecimal part, BigDecimal whole) {
        return whole.signum() == 0 ? null : part.divide(whole, 4, RoundingMode.HALF_UP).doubleValue();
    }

    private static double round(double value) {
        return Math.round(value * 10_000) / 10_000.0;
    }
}
