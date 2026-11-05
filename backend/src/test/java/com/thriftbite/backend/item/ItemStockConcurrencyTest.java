package com.thriftbite.backend.item;

import com.thriftbite.backend.TestcontainersConfiguration;
import com.thriftbite.backend.user.Role;
import com.thriftbite.backend.user.User;
import com.thriftbite.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the v1 oversell bug: in the socket version, two buyers
 * could both claim the last unit because "check stock" and "save" were
 * separate steps. Here both happen in one SQL statement.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ItemStockConcurrencyTest {

    @Autowired
    private UserRepository users;

    @Autowired
    private ItemRepository items;

    @Test
    void twoBuyersRacingForTheLastUnitOnlyOneWins() throws Exception {
        Item item = saveItem(1);

        int buyers = 2;
        CountDownLatch startTogether = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(buyers);
        List<Future<Integer>> results = new ArrayList<>();

        for (int i = 0; i < buyers; i++) {
            results.add(pool.submit(() -> {
                startTogether.await();   // both threads fire at the same moment
                return items.reserveStock(item.getId(), 1, Instant.now(), ItemStatus.AVAILABLE);
            }));
        }
        startTogether.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        int successes = 0;
        for (Future<Integer> result : results) {
            successes += result.get();
        }

        assertThat(successes).isEqualTo(1);
        assertThat(items.findById(item.getId()).orElseThrow().getQuantity()).isZero();
    }

    @Test
    void manyBuyersNeverPushStockBelowZero() throws Exception {
        Item item = saveItem(5);

        int buyers = 20;
        CountDownLatch startTogether = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(buyers);
        List<Future<Integer>> results = new ArrayList<>();

        for (int i = 0; i < buyers; i++) {
            results.add(pool.submit(() -> {
                startTogether.await();
                return items.reserveStock(item.getId(), 1, Instant.now(), ItemStatus.AVAILABLE);
            }));
        }
        startTogether.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        int successes = 0;
        for (Future<Integer> result : results) {
            successes += result.get();
        }

        assertThat(successes).isEqualTo(5);
        assertThat(items.findById(item.getId()).orElseThrow().getQuantity()).isZero();
    }

    @Test
    void cannotReserveMoreThanIsLeft() {
        Item item = saveItem(2);

        int updated = items.reserveStock(item.getId(), 3, Instant.now(), ItemStatus.AVAILABLE);

        assertThat(updated).isZero();
        assertThat(items.findById(item.getId()).orElseThrow().getQuantity()).isEqualTo(2);
    }

    @Test
    void soldOutOnlyWhenStockHitsZero() {
        Item item = saveItem(2);

        items.reserveStock(item.getId(), 1, Instant.now(), ItemStatus.AVAILABLE);
        items.markSoldOutIfEmpty(item.getId(), ItemStatus.SOLD_OUT);
        assertThat(items.findById(item.getId()).orElseThrow().getStatus()).isEqualTo(ItemStatus.AVAILABLE);

        items.reserveStock(item.getId(), 1, Instant.now(), ItemStatus.AVAILABLE);
        items.markSoldOutIfEmpty(item.getId(), ItemStatus.SOLD_OUT);
        assertThat(items.findById(item.getId()).orElseThrow().getStatus()).isEqualTo(ItemStatus.SOLD_OUT);
    }

    private Item saveItem(int quantity) {
        // Unique username per test, since all tests share one database.
        User owner = users.save(new User("owner-" + UUID.randomUUID(), "not-a-real-hash", Role.OWNER));
        Instant now = Instant.now();
        return items.save(new Item(
                owner, "Pandesal (bag of 10)", "Baked this morning",
                new BigDecimal("25.00"), new BigDecimal("50.00"), quantity,
                now.plus(Duration.ofHours(6)), "Session Road",
                now, now.plus(Duration.ofHours(4))));
    }
}
