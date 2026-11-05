package com.thriftbite.backend.reservation;

import com.jayway.jsonpath.JsonPath;
import com.thriftbite.backend.ApiTestSupport;
import com.thriftbite.backend.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CheckoutApiTest extends ApiTestSupport {

    @Test
    void checkoutSplitsTheCartIntoOneReservationPerOwner() throws Exception {
        String bakery = tokenFor(Role.OWNER);
        String carinderia = tokenFor(Role.OWNER);
        String buyer = tokenFor(Role.CONSUMER);

        String bread = createItem(bakery, "Pandesal", 10);
        String cake = createItem(bakery, "Ube cake slice", 4);
        String adobo = createItem(carinderia, "Chicken adobo", 6);

        addToCart(buyer, bread, 3).andExpect(status().isOk());
        addToCart(buyer, cake, 1).andExpect(status().isOk());
        addToCart(buyer, adobo, 2).andExpect(status().isOk())
                .andExpect(jsonPath("$.lines", hasSize(3)));

        checkout(buyer)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].status").value("RESERVED"))
                .andExpect(jsonPath("$[0].claimCode", matchesPattern("\\d{6}")))
                .andExpect(jsonPath("$[1].claimCode", matchesPattern("\\d{6}")));

        assertThat(stockOf(bread)).isEqualTo(7);
        assertThat(stockOf(cake)).isEqualTo(3);
        assertThat(stockOf(adobo)).isEqualTo(4);

        mvc.perform(get("/api/cart").header("Authorization", bearer(buyer)))
                .andExpect(jsonPath("$.lines", hasSize(0)));
    }

    @Test
    void ifOneLineIsSoldOutNothingIsReserved() throws Exception {
        String owner = tokenFor(Role.OWNER);
        String fast = tokenFor(Role.CONSUMER);
        String slow = tokenFor(Role.CONSUMER);

        String plenty = createItem(owner, "Rice (cup)", 5);
        String lastOne = createItem(owner, "Last turon", 1);

        addToCart(slow, plenty, 2).andExpect(status().isOk());
        addToCart(slow, lastOne, 1).andExpect(status().isOk());
        addToCart(fast, lastOne, 1).andExpect(status().isOk());

        checkout(fast).andExpect(status().isCreated());   // takes the last turon first

        checkout(slow).andExpect(status().isConflict());

        // all or nothing: the rice wasn't taken either, and the slow buyer's cart is untouched
        assertThat(stockOf(plenty)).isEqualTo(5);
        mvc.perform(get("/api/cart").header("Authorization", bearer(slow)))
                .andExpect(jsonPath("$.lines", hasSize(2)));
        mvc.perform(get("/api/reservations").header("Authorization", bearer(slow)))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void emptyCartCannotBeCheckedOut() throws Exception {
        checkout(tokenFor(Role.CONSUMER)).andExpect(status().isBadRequest());
    }

    @Test
    void cartWontHoldMoreThanIsInStock() throws Exception {
        String item = createItem(tokenFor(Role.OWNER), "Halo-halo", 2);
        addToCart(tokenFor(Role.CONSUMER), item, 3)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Only 2 left"));
    }

    @Test
    void ownerCompletesPickupOnlyWithTheRightClaimCode() throws Exception {
        String owner = tokenFor(Role.OWNER);
        String otherOwner = tokenFor(Role.OWNER);
        String buyer = tokenFor(Role.CONSUMER);
        String item = createItem(owner, "Bibingka", 3);
        addToCart(buyer, item, 1).andExpect(status().isOk());

        String body = checkout(buyer).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String reservationId = JsonPath.read(body, "$[0].id");
        String code = JsonPath.read(body, "$[0].claimCode");
        String wrongCode = code.equals("000000") ? "111111" : "000000";

        // the owner's list never shows the code: the buyer has to show it
        mvc.perform(get("/api/owner/reservations").header("Authorization", bearer(owner)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].claimCode").value(nullValue()));

        complete(otherOwner, reservationId, code).andExpect(status().isNotFound());
        complete(owner, reservationId, wrongCode).andExpect(status().isBadRequest());
        complete(owner, reservationId, code)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        complete(owner, reservationId, code).andExpect(status().isConflict());   // can't complete twice
    }

    @Test
    void cancellingPutsTheFoodBackOnSale() throws Exception {
        String owner = tokenFor(Role.OWNER);
        String buyer = tokenFor(Role.CONSUMER);
        String item = createItem(owner, "Sapin-sapin", 2);
        addToCart(buyer, item, 2).andExpect(status().isOk());

        String reservationId = JsonPath.read(checkout(buyer).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$[0].id");
        mvc.perform(get("/api/items/{id}", item)).andExpect(jsonPath("$.status").value("SOLD_OUT"));

        mvc.perform(post("/api/reservations/{id}/cancel", reservationId).header("Authorization", bearer(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mvc.perform(get("/api/items/{id}", item))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.quantity").value(2));
    }

    @Test
    void buyersCannotCancelSomeoneElsesReservation() throws Exception {
        String owner = tokenFor(Role.OWNER);
        String buyer = tokenFor(Role.CONSUMER);
        String stranger = tokenFor(Role.CONSUMER);
        String item = createItem(owner, "Puto bumbong", 3);
        addToCart(buyer, item, 1).andExpect(status().isOk());
        String reservationId = JsonPath.read(checkout(buyer).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$[0].id");

        mvc.perform(post("/api/reservations/{id}/cancel", reservationId).header("Authorization", bearer(stranger)))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownersCannotUseTheCart() throws Exception {
        String owner = tokenFor(Role.OWNER);
        mvc.perform(get("/api/cart").header("Authorization", bearer(owner))).andExpect(status().isForbidden());
        checkout(owner).andExpect(status().isForbidden());
    }

    // --- helpers ---

    private String createItem(String ownerToken, String name, int quantity) throws Exception {
        Instant now = Instant.now();
        String body = mvc.perform(post("/api/owner/items")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "price": 20.00,
                                  "originalPrice": 40.00,
                                  "quantity": %d,
                                  "expiresAt": "%s",
                                  "pickupLocation": "Bonifacio St, Baguio",
                                  "pickupStart": "%s",
                                  "pickupEnd": "%s"
                                }
                                """.formatted(name + " " + UUID.randomUUID().toString().substring(0, 6), quantity,
                                now.plus(Duration.ofHours(6)), now, now.plus(Duration.ofHours(3)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions addToCart(String token, String itemId, int quantity) throws Exception {
        return mvc.perform(put("/api/cart/items/{id}", itemId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\": " + quantity + "}"));
    }

    private ResultActions checkout(String token) throws Exception {
        return mvc.perform(post("/api/checkout").header("Authorization", bearer(token)));
    }

    private ResultActions complete(String ownerToken, String reservationId, String code) throws Exception {
        return mvc.perform(post("/api/owner/reservations/{id}/complete", reservationId)
                .header("Authorization", bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"claimCode\": \"" + code + "\"}"));
    }

    private int stockOf(String itemId) throws Exception {
        String body = mvc.perform(get("/api/items/{id}", itemId)).andReturn().getResponse().getContentAsString();
        Integer quantity = JsonPath.read(body, "$.quantity");
        return quantity;
    }
}
