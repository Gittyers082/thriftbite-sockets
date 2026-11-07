package com.thriftbite.backend.common;

import com.thriftbite.backend.item.ItemRepository;
import com.thriftbite.backend.item.ItemStatus;
import com.thriftbite.backend.reservation.ReservationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Housekeeping:
 * 1. reservations whose pickup deadline passed are expired and their food is restocked;
 * 2. then food past its expiry time is taken off sale (including anything just restocked).
 *
 * It runs on a schedule, and also on demand from {@link LazyExpiryInterceptor}
 * before requests that read or change stock. Free hosting tiers put the app to
 * sleep when nobody uses it, and a sleeping app runs no schedule, so food held
 * by a missed pickup would stay off sale until the next scheduled run. Running
 * it on demand (at most once per {@code lazy-min-gap}) means the first request
 * after waking sees the real stock.
 *
 * Browsing and checkout already ignore expired food on their own, so if this
 * runs late, nobody can buy expired food in the meantime.
 */
@Component
public class ExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(ExpiryJob.class);

    private final ReservationService reservations;
    private final ItemRepository items;
    private final Duration lazyMinGap;

    /** One run at a time: the scheduled run and an on-demand run must not restock the same food twice. */
    private final ReentrantLock running = new ReentrantLock();
    private volatile Instant lastRun = Instant.EPOCH;

    public ExpiryJob(ReservationService reservations, ItemRepository items,
                     @Value("${app.expiry.lazy-min-gap:PT30S}") Duration lazyMinGap) {
        this.reservations = reservations;
        this.items = items;
        this.lazyMinGap = lazyMinGap;
    }

    /** Always runs, waiting for a run in progress to finish first. */
    @Scheduled(fixedDelayString = "${app.expiry.interval:PT5M}",
               initialDelayString = "${app.expiry.initial-delay:PT1M}")
    public void run() {
        running.lock();
        try {
            sweep();
        } finally {
            running.unlock();
        }
    }

    /**
     * Runs if the last run was more than {@code lazy-min-gap} ago and no run is
     * in progress; otherwise returns at once. Never throws, so a failed sweep
     * can't break the request that triggered it.
     */
    public void runIfDue() {
        if (Duration.between(lastRun, Instant.now()).compareTo(lazyMinGap) < 0 || !running.tryLock()) {
            return;
        }
        try {
            if (Duration.between(lastRun, Instant.now()).compareTo(lazyMinGap) >= 0) {
                sweep();
            }
        } catch (RuntimeException e) {
            // e.g. a buyer cancelled the same reservation at that moment; the next run picks it up
            log.warn("On-demand expiry run failed, will retry on the next one: {}", e.toString());
        } finally {
            running.unlock();
        }
    }

    private void sweep() {
        Instant now = Instant.now();
        lastRun = now;
        int missed = reservations.expireMissedPickups(now);
        int expired = items.expireOldItems(now, ItemStatus.EXPIRED, List.of(ItemStatus.AVAILABLE, ItemStatus.SOLD_OUT));
        if (missed > 0 || expired > 0) {
            log.info("Expiry run: {} missed pickups, {} items expired", missed, expired);
        }
    }
}
