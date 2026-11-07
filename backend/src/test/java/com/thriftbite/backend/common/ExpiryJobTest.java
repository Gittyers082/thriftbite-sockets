package com.thriftbite.backend.common;

import com.thriftbite.backend.ApiTestSupport;
import com.thriftbite.backend.item.Item;
import com.thriftbite.backend.item.ItemRepository;
import com.thriftbite.backend.item.ItemStatus;
import com.thriftbite.backend.reservation.Reservation;
import com.thriftbite.backend.reservation.ReservationItem;
import com.thriftbite.backend.reservation.ReservationRepository;
import com.thriftbite.backend.reservation.ReservationStatus;
import com.thriftbite.backend.user.Role;
import com.thriftbite.backend.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExpiryJobTest extends ApiTestSupport {

    @Autowired
    private ExpiryJob job;

    @Autowired
    private ItemRepository items;

    @Autowired
    private ReservationRepository reservations;

    @Test
    void foodPastItsExpiryIsTakenOffSale() {
        User owner = saveUser(Role.OWNER);
        Instant now = Instant.now();
        Item stale = items.save(item(owner, 3, now.minus(Duration.ofMinutes(1))));
        Item fresh = items.save(item(owner, 3, now.plus(Duration.ofHours(5))));

        job.run();

        assertThat(items.findById(stale.getId()).orElseThrow().getStatus()).isEqualTo(ItemStatus.EXPIRED);
        assertThat(items.findById(fresh.getId()).orElseThrow().getStatus()).isEqualTo(ItemStatus.AVAILABLE);
    }

    @Test
    void missedPickupsExpireAndTheFoodGoesBackOnSale() {
        User owner = saveUser(Role.OWNER);
        User buyer = saveUser(Role.CONSUMER);
        Instant now = Instant.now();

        // all 2 units were reserved, so the item is sold out
        Item item = items.save(item(owner, 0, now.plus(Duration.ofHours(5))));
        Reservation reservation = new Reservation(buyer, owner, "123456", now.minus(Duration.ofMinutes(5)));
        reservation.addItem(new ReservationItem(item, 2));
        reservation = reservations.save(reservation);

        job.run();

        assertThat(reservations.findById(reservation.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.EXPIRED);
        Item restocked = items.findById(item.getId()).orElseThrow();
        assertThat(restocked.getQuantity()).isEqualTo(2);
        assertThat(restocked.getStatus()).isEqualTo(ItemStatus.AVAILABLE);
    }

    /**
     * A host that sleeps runs no schedule. The first request that looks at
     * stock releases missed pickups on its own, so the buyer sees real stock.
     */
    @Test
    void browsingReleasesMissedPickupsWithoutWaitingForTheSchedule() throws Exception {
        User owner = saveUser(Role.OWNER);
        User buyer = saveUser(Role.CONSUMER);
        Instant now = Instant.now();
        Item item = items.save(item(owner, 0, now.plus(Duration.ofHours(5))));
        Reservation reservation = new Reservation(buyer, owner, "654321", now.minus(Duration.ofMinutes(1)));
        reservation.addItem(new ReservationItem(item, 3));
        reservation = reservations.save(reservation);

        mvc.perform(get("/api/items/{id}", item.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(3))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
        assertThat(reservations.findById(reservation.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.EXPIRED);
    }

    private User saveUser(Role role) {
        User user = new User(uniqueName(), "not-a-real-hash", role);
        user.approve();
        return users.save(user);
    }

    private static Item item(User owner, int quantity, Instant expiresAt) {
        return new Item(owner, "Test food", null,
                new BigDecimal("10.00"), new BigDecimal("20.00"), quantity,
                expiresAt, "Baguio", expiresAt.minus(Duration.ofHours(3)), expiresAt.minus(Duration.ofHours(2)));
    }
}
