package com.thriftbite.backend.common;

import com.thriftbite.backend.item.ItemRepository;
import com.thriftbite.backend.item.ItemStatus;
import com.thriftbite.backend.reservation.ReservationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Housekeeping that runs every few minutes:
 * 1. reservations whose pickup deadline passed are expired and their food is restocked;
 * 2. then food past its expiry time is taken off sale (including anything just restocked).
 *
 * Browsing and checkout already ignore expired food on their own, so this job
 * only keeps statuses accurate for owners and buyers. If it runs late, nobody
 * can buy expired food in the meantime.
 */
@Component
public class ExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(ExpiryJob.class);

    private final ReservationService reservations;
    private final ItemRepository items;

    public ExpiryJob(ReservationService reservations, ItemRepository items) {
        this.reservations = reservations;
        this.items = items;
    }

    @Scheduled(fixedDelayString = "${app.expiry.interval:PT5M}",
               initialDelayString = "${app.expiry.initial-delay:PT1M}")
    public void run() {
        Instant now = Instant.now();
        int missed = reservations.expireMissedPickups(now);
        int expired = items.expireOldItems(now, ItemStatus.EXPIRED, List.of(ItemStatus.AVAILABLE, ItemStatus.SOLD_OUT));
        if (missed > 0 || expired > 0) {
            log.info("Expiry job: {} missed pickups, {} items expired", missed, expired);
        }
    }
}
