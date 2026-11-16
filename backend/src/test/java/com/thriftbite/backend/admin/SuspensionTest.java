package com.thriftbite.backend.admin;

import com.jayway.jsonpath.JsonPath;
import com.thriftbite.backend.ApiTestSupport;
import com.thriftbite.backend.user.Role;
import com.thriftbite.backend.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admins suspending accounts: who gets locked out, and what happens to their food and pickups. */
class SuspensionTest extends ApiTestSupport {

    private static final String REASON = "Three buyers reported spoiled food this week.";

    /** An account whose id and username the test knows, logged in. */
    private record Account(UUID id, String username, String token) {
    }

    @Test
    void suspendingAStoreLocksItOutTakesItsFoodDownAndCancelsPickups() throws Exception {
        String admin = tokenFor(Role.ADMIN);
        Account owner = account(Role.OWNER);
        Account buyer = account(Role.CONSUMER);
        String slug = JsonPath.read(mvc.perform(get("/api/owner/store").header("Authorization", bearer(owner.token())))
                .andReturn().getResponse().getContentAsString(), "$.slug");
        String itemId = createItem(owner.token(), 5);
        reserve(buyer.token(), itemId, 2);

        suspend(admin, owner.id(), "  " + REASON + "  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suspendedReason").value(REASON));

        // Locked out on the token it already had, and told why when it logs in again.
        mvc.perform(get("/api/owner/items").header("Authorization", bearer(owner.token()))).andExpect(status().isUnauthorized());
        login(owner.username(), PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("account_suspended"))
                .andExpect(jsonPath("$.detail", containsString(REASON)));
        // Someone guessing the password learns nothing about the suspension.
        login(owner.username(), "not-the-password").andExpect(status().isUnauthorized());

        // The store and its food are off the app.
        mvc.perform(get("/api/stores/{slug}", slug)).andExpect(status().isNotFound());
        mvc.perform(get("/api/items/{id}", itemId)).andExpect(status().isNotFound());

        // The buyer's pickup is cancelled (not missed), and they're told.
        mvc.perform(get("/api/reservations").header("Authorization", bearer(buyer.token())))
                .andExpect(jsonPath("$[0].status").value("CANCELLED"));
        mvc.perform(get("/api/alerts").header("Authorization", bearer(buyer.token())))
                .andExpect(jsonPath("$.alerts", hasSize(1)))
                .andExpect(jsonPath("$.alerts[0].kind").value("CANCELLED"));
    }

    @Test
    void suspendingABuyerPutsWhatTheyHeldBackOnSale() throws Exception {
        String admin = tokenFor(Role.ADMIN);
        Account buyer = account(Role.CONSUMER);
        String itemId = createItem(tokenFor(Role.OWNER), 5);
        reserve(buyer.token(), itemId, 3);
        mvc.perform(get("/api/items/{id}", itemId)).andExpect(jsonPath("$.quantity").value(2));

        suspend(admin, buyer.id(), REASON).andExpect(status().isOk());

        mvc.perform(get("/api/items/{id}", itemId)).andExpect(jsonPath("$.quantity").value(5));
        mvc.perform(get("/api/reservations").header("Authorization", bearer(buyer.token()))).andExpect(status().isUnauthorized());
    }

    @Test
    void liftingASuspensionLetsTheAccountBackIn() throws Exception {
        String admin = tokenFor(Role.ADMIN);
        Account buyer = account(Role.CONSUMER);
        suspend(admin, buyer.id(), REASON).andExpect(status().isOk());

        mvc.perform(post("/api/admin/users/{id}/unsuspend", buyer.id()).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suspendedAt").value(nullValue()));

        login(buyer.username(), PASSWORD).andExpect(status().isOk());
        mvc.perform(post("/api/admin/users/{id}/unsuspend", buyer.id()).header("Authorization", bearer(admin)))
                .andExpect(status().isConflict());
    }

    @Test
    void whoCanSuspendAndWhoCannotBeSuspended() throws Exception {
        Account admin = account(Role.ADMIN);
        Account owner = account(Role.OWNER);
        Account buyer = account(Role.CONSUMER);

        suspend(owner.token(), buyer.id(), REASON).andExpect(status().isForbidden());
        suspend(buyer.token(), owner.id(), REASON).andExpect(status().isForbidden());
        suspend(admin.token(), admin.id(), REASON).andExpect(status().isForbidden());      // admins, themselves included
        suspend(admin.token(), buyer.id(), "bad").andExpect(status().isBadRequest());      // needs a real reason
        suspend(admin.token(), UUID.randomUUID(), REASON).andExpect(status().isNotFound());

        suspend(admin.token(), buyer.id(), REASON).andExpect(status().isOk());
        suspend(admin.token(), buyer.id(), REASON).andExpect(status().isConflict());
    }

    @Test
    void theListShowsBuyersAndStoresAndTheLogRecordsSuspensions() throws Exception {
        Account admin = account(Role.ADMIN);
        Account buyer = account(Role.CONSUMER);

        mvc.perform(get("/api/admin/users").param("q", buyer.username()).header("Authorization", bearer(admin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].role").value("CONSUMER"))
                .andExpect(jsonPath("$[0].suspendedAt").value(nullValue()));
        // Admins aren't in the list.
        mvc.perform(get("/api/admin/users").param("q", admin.username()).header("Authorization", bearer(admin.token())))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/admin/users").header("Authorization", bearer(buyer.token()))).andExpect(status().isForbidden());

        suspend(admin.token(), buyer.id(), REASON).andExpect(status().isOk());

        mvc.perform(get("/api/admin/users").param("show", "suspended").param("q", buyer.username())
                        .header("Authorization", bearer(admin.token())))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].suspendedReason").value(REASON));
        mvc.perform(get("/api/admin/activity").header("Authorization", bearer(admin.token())))
                .andExpect(jsonPath("$[0].action").value("SUSPEND_ACCOUNT"))
                .andExpect(jsonPath("$[0].subject").value(buyer.username()))
                .andExpect(jsonPath("$[0].note", containsString(REASON)));
    }

    // --- helpers ---

    /** Like tokenFor, but keeps the id and username so the test can act on the account. */
    private Account account(Role role) throws Exception {
        User user = new User(uniqueName(), passwordEncoder.encode(PASSWORD), role);
        user.approve();
        users.save(user);
        if (role == Role.OWNER) {
            stores.createForOwner(user, "Store of " + user.getUsername());
        }
        return new Account(user.getId(), user.getUsername(), login(user.getUsername()));
    }

    private ResultActions suspend(String token, UUID userId, String reason) throws Exception {
        return mvc.perform(post("/api/admin/users/{id}/suspend", userId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"" + reason + "\"}"));
    }

    private ResultActions login(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, password)));
    }

    private String createItem(String ownerToken, int quantity) throws Exception {
        Instant now = Instant.now();
        String body = mvc.perform(post("/api/owner/items")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Pandesal",
                                  "price": 20.00,
                                  "originalPrice": 40.00,
                                  "quantity": %d,
                                  "expiresAt": "%s",
                                  "pickupLocation": "Bonifacio St, Baguio",
                                  "pickupStart": "%s",
                                  "pickupEnd": "%s"
                                }
                                """.formatted(quantity, now.plus(Duration.ofHours(6)), now, now.plus(Duration.ofHours(3)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void reserve(String buyerToken, String itemId, int quantity) throws Exception {
        mvc.perform(put("/api/cart/items/{id}", itemId)
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": " + quantity + "}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/checkout").header("Authorization", bearer(buyerToken))).andExpect(status().isCreated());
    }
}
