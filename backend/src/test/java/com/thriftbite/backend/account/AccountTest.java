package com.thriftbite.backend.account;

import com.jayway.jsonpath.JsonPath;
import com.thriftbite.backend.ApiTestSupport;
import com.thriftbite.backend.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Recovery codes, password reset and change, the account page, and deleting an account. */
class AccountTest extends ApiTestSupport {

    private static final String NEW_PASSWORD = "a-brand-new-password";

    /** A buyer who signed up through the API, so they have recovery codes. */
    private record Signup(String username, List<String> codes) {
    }

    @Test
    void aRecoveryCodeResetsThePasswordOnceAndLogsOutOldLogins() throws Exception {
        Signup buyer = signUp();
        assertThat(buyer.codes()).hasSize(8).doesNotHaveDuplicates();
        String oldLogin = login(buyer.username());

        // Checking a code (no new password) doesn't use it up.
        recover(buyer.username(), buyer.codes().get(0), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(nullValue()))
                .andExpect(jsonPath("$.recoveryCodesLeft").value(8));

        // Typed in lower case without dashes, as people do.
        String typed = buyer.codes().get(0).toLowerCase().replace("-", " ");
        String body = recover(buyer.username(), typed, NEW_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value(buyer.username()))
                .andExpect(jsonPath("$.recoveryCodesLeft").value(7))
                .andReturn().getResponse().getContentAsString();
        String newLogin = JsonPath.read(body, "$.token");

        me(oldLogin).andExpect(status().isUnauthorized());   // logged out everywhere else
        me(newLogin).andExpect(status().isOk());
        loginWith(buyer.username(), PASSWORD).andExpect(status().isUnauthorized());
        loginWith(buyer.username(), NEW_PASSWORD).andExpect(status().isOk());

        // Each code works once.
        recover(buyer.username(), buyer.codes().get(0), "yet-another-password").andExpect(status().isBadRequest());
        loginWith(buyer.username(), NEW_PASSWORD).andExpect(status().isOk());
    }

    /** Same answer either way, so this can't be used to find out which usernames exist. */
    @Test
    void aWrongCodeAndAnUnknownUsernameGetTheSameAnswer() throws Exception {
        Signup buyer = signUp();
        String wrongCode = recover(buyer.username(), "AAAA-BBBB-CCCC", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        String noSuchUser = recover(uniqueName(), "AAAA-BBBB-CCCC", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(wrongCode, "$.detail")).isEqualTo(JsonPath.<String>read(noSuchUser, "$.detail"));
        loginWith(buyer.username(), PASSWORD).andExpect(status().isOk());   // nothing changed
    }

    @Test
    void changingThePasswordNeedsTheCurrentOneAndLogsOutOtherDevices() throws Exception {
        Signup buyer = signUp();
        String thisDevice = login(buyer.username());
        String otherDevice = login(buyer.username());

        changePassword(thisDevice, "not-my-password", NEW_PASSWORD).andExpect(status().isBadRequest());
        changePassword(thisDevice, PASSWORD, "short").andExpect(status().isBadRequest());
        changePassword(thisDevice, PASSWORD, PASSWORD).andExpect(status().isBadRequest());   // not a change

        String body = changePassword(thisDevice, PASSWORD, NEW_PASSWORD)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        me(JsonPath.read(body, "$.token")).andExpect(status().isOk());   // this device carries on
        me(otherDevice).andExpect(status().isUnauthorized());
        me(thisDevice).andExpect(status().isUnauthorized());            // its old token is gone too
        loginWith(buyer.username(), NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void newRecoveryCodesNeedThePasswordAndReplaceTheOldSet() throws Exception {
        Signup buyer = signUp();
        String token = login(buyer.username());

        newCodes(token, "not-my-password").andExpect(status().isBadRequest());
        String body = newCodes(token, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codes", hasSize(8)))
                .andReturn().getResponse().getContentAsString();
        List<String> fresh = JsonPath.read(body, "$.codes");

        assertThat(fresh).doesNotContainAnyElementsOf(buyer.codes());
        recover(buyer.username(), buyer.codes().get(0), null).andExpect(status().isBadRequest());
        recover(buyer.username(), fresh.get(0), null).andExpect(status().isOk());
    }

    @Test
    void theAccountPageAddsUpWhatABuyerSaved() throws Exception {
        String owner = tokenFor(Role.OWNER);
        String buyer = tokenFor(Role.CONSUMER);
        String itemId = createItem(owner, 5);   // ₱20 instead of ₱40
        String reservation = reserve(buyer, itemId, 2);
        mvc.perform(post("/api/owner/reservations/{id}/complete", JsonPath.<String>read(reservation, "$[0].id"))
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"claimCode\": \"" + JsonPath.<String>read(reservation, "$[0].claimCode") + "\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/account").header("Authorization", bearer(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CONSUMER"))
                .andExpect(jsonPath("$.buyer.saved").value(40.00))   // 2 x (40 - 20)
                .andExpect(jsonPath("$.buyer.mealsSaved").value(2))
                .andExpect(jsonPath("$.buyer.months", hasSize(3)))
                .andExpect(jsonPath("$.buyer.months[2].saved").value(40.00))
                .andExpect(jsonPath("$.buyer.missedPickups", hasSize(0)))
                .andExpect(jsonPath("$.buyer.activeReservations").value(0));

        // Stores have no savings to show.
        mvc.perform(get("/api/account").header("Authorization", bearer(owner)))
                .andExpect(jsonPath("$.buyer").value(nullValue()));
        mvc.perform(get("/api/account")).andExpect(status().isUnauthorized());
    }

    @Test
    void downloadMyDataHoldsYourOwnReservationsOrYourOwnStore() throws Exception {
        String owner = tokenFor(Role.OWNER);
        String buyer = tokenFor(Role.CONSUMER);
        reserve(buyer, createItem(owner, 5), 1);

        mvc.perform(get("/api/account/export").header("Authorization", bearer(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservations", hasSize(1)))
                .andExpect(jsonPath("$.reservations[0].claimCode").isString())
                .andExpect(jsonPath("$.store").value(nullValue()));
        // The store gets its profile and listings, and nobody's claim codes.
        mvc.perform(get("/api/account/export").header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listings", hasSize(1)))
                .andExpect(jsonPath("$.store.slug").isString())
                .andExpect(jsonPath("$.reservations").value(nullValue()));
    }

    @Test
    void aBuyerCanDeleteTheirAccountAndWhatTheyHeldGoesBackOnSale() throws Exception {
        String owner = tokenFor(Role.OWNER);
        String itemId = createItem(owner, 5);
        Signup buyer = signUp();
        String token = login(buyer.username());
        reserve(token, itemId, 2);
        assertThat(stockOf(itemId)).isEqualTo(3);

        delete(token, "not-my-password").andExpect(status().isBadRequest());
        delete(token, PASSWORD).andExpect(status().isNoContent());

        assertThat(stockOf(itemId)).isEqualTo(5);                                  // the food is back on sale
        me(token).andExpect(status().isUnauthorized());                            // logged out at once
        loginWith(buyer.username(), PASSWORD).andExpect(status().isUnauthorized());
        recover(buyer.username(), buyer.codes().get(0), NEW_PASSWORD).andExpect(status().isBadRequest());
        // The row stays for the store's records, without the username.
        assertThat(users.findByUsername(buyer.username())).isEmpty();
        // The store still sees the cancelled reservation's stock history; its sales page still loads.
        mvc.perform(get("/api/owner/stats").header("Authorization", bearer(owner))).andExpect(status().isOk());
        // The username is free again.
        register(buyer.username()).andExpect(status().isCreated());
    }

    @Test
    void storeAccountsCantDeleteThemselves() throws Exception {
        delete(tokenFor(Role.OWNER), PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail", startsWith("Store and admin accounts")));
    }

    // --- helpers ---

    private Signup signUp() throws Exception {
        String username = uniqueName();
        String body = register(username)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andReturn().getResponse().getContentAsString();
        return new Signup(username, JsonPath.read(body, "$.recoveryCodes"));
    }

    private ResultActions register(String username) throws Exception {
        return mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username": "%s", "password": "%s", "accountType": "CONSUMER"}
                        """.formatted(username, PASSWORD)));
    }

    private ResultActions loginWith(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username": "%s", "password": "%s"}
                        """.formatted(username, password)));
    }

    private ResultActions recover(String username, String code, String newPassword) throws Exception {
        String password = newPassword == null ? "" : ", \"newPassword\": \"" + newPassword + "\"";
        return mvc.perform(post("/api/auth/recover")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"" + username + "\", \"recoveryCode\": \"" + code + "\"" + password + "}"));
    }

    private ResultActions me(String token) throws Exception {
        return mvc.perform(get("/api/auth/me").header("Authorization", bearer(token)));
    }

    private ResultActions changePassword(String token, String current, String next) throws Exception {
        return mvc.perform(post("/api/account/password")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currentPassword": "%s", "newPassword": "%s"}
                        """.formatted(current, next)));
    }

    private ResultActions newCodes(String token, String password) throws Exception {
        return mvc.perform(post("/api/account/recovery-codes")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\": \"" + password + "\"}"));
    }

    private ResultActions delete(String token, String password) throws Exception {
        return mvc.perform(post("/api/account/delete")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\": \"" + password + "\"}"));
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

    /** Puts the item in the cart and checks out. Returns the checkout response. */
    private String reserve(String buyerToken, String itemId, int quantity) throws Exception {
        mvc.perform(put("/api/cart/items/{id}", itemId)
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": " + quantity + "}"))
                .andExpect(status().isOk());
        return mvc.perform(post("/api/checkout").header("Authorization", bearer(buyerToken)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private int stockOf(String itemId) throws Exception {
        String body = mvc.perform(get("/api/items/{id}", itemId)).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.quantity");
    }
}
