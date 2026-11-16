package com.thriftbite.backend.reservation;

import com.jayway.jsonpath.JsonPath;
import com.thriftbite.backend.ApiTestSupport;
import com.thriftbite.backend.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The missed-pickup rule: 2 misses within 30 days pause reserving for 7 days. */
class PickupStrikesTest extends ApiTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void twoMissedPickupsWithinThirtyDaysPauseReserving() throws Exception {
        String owner = tokenFor(Role.OWNER);
        String buyer = tokenFor(Role.CONSUMER);
        miss(buyer, owner, 10);
        addToCart(buyer, createItem(owner));
        checkout(buyer).andExpect(status().isCreated());   // one miss: still fine

        miss(buyer, owner, 2);
        addToCart(buyer, createItem(owner));
        checkout(buyer)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("pickup_block"))
                .andExpect(jsonPath("$.until").value(notNullValue()))
                .andExpect(jsonPath("$.detail", containsString("can't reserve until")));

        // The account page is told, and the cart is still there for later.
        mvc.perform(get("/api/account").header("Authorization", bearer(buyer)))
                .andExpect(jsonPath("$.buyer.reserveBlockedUntil").value(notNullValue()))
                .andExpect(jsonPath("$.buyer.missedPickups", hasSize(2)));
        mvc.perform(get("/api/cart").header("Authorization", bearer(buyer))).andExpect(jsonPath("$.lines", hasSize(1)));
    }

    @Test
    void theBlockEndsSevenDaysAfterTheLatestMiss() throws Exception {
        String owner = tokenFor(Role.OWNER);
        String buyer = tokenFor(Role.CONSUMER);
        miss(buyer, owner, 20);
        miss(buyer, owner, 8);   // both within 30 days, but the latest was over 7 days ago

        addToCart(buyer, createItem(owner));
        checkout(buyer).andExpect(status().isCreated());
        mvc.perform(get("/api/account").header("Authorization", bearer(buyer)))
                .andExpect(jsonPath("$.buyer.reserveBlockedUntil").value(nullValue()));
    }

    @Test
    void missesFarApartDoNotAddUp() throws Exception {
        String owner = tokenFor(Role.OWNER);
        String buyer = tokenFor(Role.CONSUMER);
        miss(buyer, owner, 45);
        miss(buyer, owner, 1);   // 44 days after the first

        addToCart(buyer, createItem(owner));
        checkout(buyer).andExpect(status().isCreated());
    }

    @Test
    void cancellingInTimeNeverCounts() throws Exception {
        String owner = tokenFor(Role.OWNER);
        String buyer = tokenFor(Role.CONSUMER);
        miss(buyer, owner, 1);
        for (int i = 0; i < 3; i++) {
            String reservationId = reserve(buyer, createItem(owner));
            mvc.perform(post("/api/reservations/{id}/cancel", reservationId).header("Authorization", bearer(buyer)))
                    .andExpect(status().isOk());
        }

        addToCart(buyer, createItem(owner));
        checkout(buyer).andExpect(status().isCreated());
    }

    @Test
    void anAdminCanExcuseAMissThatWasNotTheBuyersFault() throws Exception {
        String admin = tokenFor(Role.ADMIN);
        String owner = tokenFor(Role.OWNER);
        String buyer = tokenFor(Role.CONSUMER);
        miss(buyer, owner, 5);
        String closedThatDay = miss(buyer, owner, 1);
        addToCart(buyer, createItem(owner));
        checkout(buyer).andExpect(status().isForbidden());

        String reportId = JsonPath.read(mvc.perform(post("/api/reports")
                        .header("Authorization", bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reservationId\": \"%s\", \"kind\": \"STORE_CLOSED\"}".formatted(closedThatDay)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/api/admin/reports/{id}/resolve", reportId)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reply\": \"The store closed early. That miss no longer counts.\", \"excuseMiss\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pickupMissed").value(false));

        checkout(buyer).andExpect(status().isCreated());
        mvc.perform(get("/api/account").header("Authorization", bearer(buyer)))
                .andExpect(jsonPath("$.buyer.missedPickups", hasSize(1)));
    }

    // --- helpers ---

    /** A pickup the buyer didn't come for, {@code daysAgo} days ago; returns the reservation's id. */
    private String miss(String buyerToken, String ownerToken, int daysAgo) throws Exception {
        String reservationId = reserve(buyerToken, createItem(ownerToken));
        jdbc.update("""
                UPDATE reservations
                   SET status = 'EXPIRED', expired_at = now() - make_interval(days => ?),
                       pickup_deadline = now() - make_interval(days => ?)
                 WHERE id = ?::uuid
                """, daysAgo, daysAgo, reservationId);
        return reservationId;
    }

    private String reserve(String buyerToken, String itemId) throws Exception {
        addToCart(buyerToken, itemId);
        String body = checkout(buyerToken).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$[0].id");
    }

    private void addToCart(String buyerToken, String itemId) throws Exception {
        mvc.perform(put("/api/cart/items/{id}", itemId)
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 1}"))
                .andExpect(status().isOk());
    }

    private ResultActions checkout(String buyerToken) throws Exception {
        return mvc.perform(post("/api/checkout").header("Authorization", bearer(buyerToken)));
    }

    private String createItem(String ownerToken) throws Exception {
        Instant now = Instant.now();
        String body = mvc.perform(post("/api/owner/items")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Pandesal",
                                  "price": 20.00,
                                  "originalPrice": 40.00,
                                  "quantity": 5,
                                  "expiresAt": "%s",
                                  "pickupLocation": "Bonifacio St, Baguio",
                                  "pickupStart": "%s",
                                  "pickupEnd": "%s"
                                }
                                """.formatted(now.plus(Duration.ofHours(6)), now, now.plus(Duration.ofHours(3)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }
}
