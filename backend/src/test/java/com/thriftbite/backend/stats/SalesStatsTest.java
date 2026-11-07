package com.thriftbite.backend.stats;

import com.jayway.jsonpath.JsonPath;
import com.thriftbite.backend.ApiTestSupport;
import com.thriftbite.backend.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SalesStatsTest extends ApiTestSupport {

    private static final String TODAY = LocalDate.now(SalesStatsService.ZONE).toString();

    @Test
    void todaysSalesAddUp() throws Exception {
        String owner = tokenFor(Role.OWNER);
        String item = createItem(owner, "Ensaymada", 4, "30.00", "60.00");

        // one pickup handed over (2 units), one reservation cancelled (1 unit)
        String handedOver = reserve(tokenFor(Role.CONSUMER), item, 2);
        complete(owner, handedOver);
        String cancelling = tokenFor(Role.CONSUMER);
        String cancelledId = JsonPath.read(reserve(cancelling, item, 1), "$[0].id");
        mvc.perform(post("/api/reservations/{id}/cancel", cancelledId).header("Authorization", bearer(cancelling)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/owner/stats").param("from", TODAY).param("to", TODAY)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").value("Asia/Manila"))
                .andExpect(jsonPath("$.days", hasSize(1)))
                .andExpect(jsonPath("$.totals.moneyMade").value(60.00))
                .andExpect(jsonPath("$.totals.unitsSold").value(2))
                .andExpect(jsonPath("$.totals.pickups").value(1))
                .andExpect(jsonPath("$.totals.discountGiven").value(60.00))
                .andExpect(jsonPath("$.totals.averageDiscount").value(0.5))
                .andExpect(jsonPath("$.totals.unitsListed").value(4))
                .andExpect(jsonPath("$.totals.unitsSoldOfListed").value(2))
                .andExpect(jsonPath("$.totals.sellThrough").value(0.5))
                .andExpect(jsonPath("$.totals.unitsLeftOver").value(0))    // still on sale, so nothing left over yet
                .andExpect(jsonPath("$.totals.completedReservations").value(1))
                .andExpect(jsonPath("$.totals.cancelledReservations").value(1))
                .andExpect(jsonPath("$.totals.missedPickups").value(0))
                .andExpect(jsonPath("$.pickupsByHour", hasSize(24)))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].listed").value(4))
                .andExpect(jsonPath("$.items[0].sold").value(2))
                .andExpect(jsonPath("$.items[0].sellThrough").value(0.5));
    }

    @Test
    void sellingOutRecordsTheTimeOfDay() throws Exception {
        String owner = tokenFor(Role.OWNER);
        String item = createItem(owner, "Cheese rolls", 1, "45.00", "90.00");
        complete(owner, reserve(tokenFor(Role.CONSUMER), item, 1));

        mvc.perform(get("/api/owner/stats").param("from", TODAY).param("to", TODAY)
                        .header("Authorization", bearer(owner)))
                .andExpect(jsonPath("$.items[0].daysSoldOut").value(1))
                .andExpect(jsonPath("$.items[0].usualSellOutTime", notNullValue()))
                .andExpect(jsonPath("$.totals.sellThrough").value(1.0));
    }

    @Test
    void eachStoreOnlySeesItsOwnSales() throws Exception {
        String owner = tokenFor(Role.OWNER);
        String item = createItem(owner, "Spanish bread", 3, "40.00", "70.00");
        complete(owner, reserve(tokenFor(Role.CONSUMER), item, 1));

        mvc.perform(get("/api/owner/stats").param("from", TODAY).param("to", TODAY)
                        .header("Authorization", bearer(tokenFor(Role.OWNER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.moneyMade").value(0))
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void theDefaultIsTheLastSevenFullDays() throws Exception {
        LocalDate yesterday = LocalDate.now(SalesStatsService.ZONE).minusDays(1);
        mvc.perform(get("/api/owner/stats").header("Authorization", bearer(tokenFor(Role.OWNER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days", hasSize(7)))
                .andExpect(jsonPath("$.to").value(yesterday.toString()))
                .andExpect(jsonPath("$.from").value(yesterday.minusDays(6).toString()))
                .andExpect(jsonPath("$.leftoverByWeekday", hasSize(7)));
    }

    @Test
    void badRangesAreRefused() throws Exception {
        String owner = tokenFor(Role.OWNER);
        LocalDate today = LocalDate.now(SalesStatsService.ZONE);
        stats(owner, today.toString(), today.minusDays(1).toString()).andExpect(status().isBadRequest());
        stats(owner, today.toString(), today.plusDays(1).toString()).andExpect(status().isBadRequest());
        stats(owner, today.minusDays(100).toString(), today.toString()).andExpect(status().isBadRequest());
        stats(owner, "not-a-date", today.toString()).andExpect(status().isBadRequest());
    }

    @Test
    void buyersCantSeeSales() throws Exception {
        mvc.perform(get("/api/owner/stats").header("Authorization", bearer(tokenFor(Role.CONSUMER))))
                .andExpect(status().isForbidden());
    }

    // --- helpers ---

    private org.springframework.test.web.servlet.ResultActions stats(String token, String from, String to)
            throws Exception {
        return mvc.perform(get("/api/owner/stats").param("from", from).param("to", to)
                .header("Authorization", bearer(token)));
    }

    private String createItem(String ownerToken, String name, int quantity, String price, String usual)
            throws Exception {
        Instant now = Instant.now();
        String body = mvc.perform(post("/api/owner/items")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "price": %s, "originalPrice": %s, "quantity": %d,
                                 "expiresAt": "%s", "pickupLocation": "Session Road, Baguio",
                                 "pickupStart": "%s", "pickupEnd": "%s"}
                                """.formatted(name, price, usual, quantity, now.plus(Duration.ofHours(6)), now,
                                now.plus(Duration.ofHours(3)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    /** Puts the item in a new cart and checks out; returns the checkout response body. */
    private String reserve(String buyerToken, String itemId, int quantity) throws Exception {
        mvc.perform(put("/api/cart/items/{id}", itemId).header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\": " + quantity + "}"))
                .andExpect(status().isOk());
        return mvc.perform(post("/api/checkout").header("Authorization", bearer(buyerToken)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    /** Hands over the first reservation in a checkout response, with its claim code. */
    private void complete(String ownerToken, String checkoutBody) throws Exception {
        String id = JsonPath.read(checkoutBody, "$[0].id");
        String code = JsonPath.read(checkoutBody, "$[0].claimCode");
        mvc.perform(post("/api/owner/reservations/{id}/complete", id)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"claimCode\": \"" + code + "\"}"))
                .andExpect(status().isOk());
    }
}
