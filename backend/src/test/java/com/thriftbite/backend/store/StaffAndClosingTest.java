package com.thriftbite.backend.store;

import com.jayway.jsonpath.JsonPath;
import com.thriftbite.backend.ApiTestSupport;
import com.thriftbite.backend.user.Role;
import com.thriftbite.backend.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Staff accounts, what they may do, and an owner closing the store. */
class StaffAndClosingTest extends ApiTestSupport {

    /** A store's owner whose username the test knows. */
    private record Owner(String username, String token) {
    }

    /** A staff account as the owner received it, logged in. */
    private record Staff(String id, String username, String password, String token) {
    }

    @Test
    void theOwnerAddsStaffWhoCanWorkTheCounterButNotRunTheStore() throws Exception {
        Owner owner = owner();
        Staff staff = addStaff(owner);

        mvc.perform(get("/api/auth/me").header("Authorization", bearer(staff.token())))
                .andExpect(jsonPath("$.staff").value(true))
                .andExpect(jsonPath("$.role").value("OWNER"));
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(owner.token())))
                .andExpect(jsonPath("$.staff").value(false));

        // The counter work: listing food and seeing pickups.
        createItem(staff.token()).andExpect(status().isCreated());
        mvc.perform(get("/api/owner/items").header("Authorization", bearer(owner.token()))).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/owner/reservations").header("Authorization", bearer(staff.token()))).andExpect(status().isOk());
        mvc.perform(get("/api/owner/store").header("Authorization", bearer(staff.token()))).andExpect(status().isOk());

        // The owner's business.
        mvc.perform(get("/api/owner/stats").header("Authorization", bearer(staff.token()))).andExpect(status().isForbidden());
        mvc.perform(get("/api/owner/staff").header("Authorization", bearer(staff.token()))).andExpect(status().isForbidden());
        mvc.perform(put("/api/owner/store")
                        .header("Authorization", bearer(staff.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Renamed\", \"gcashAccepted\": false, \"hours\": [], \"version\": 0}"))
                .andExpect(status().isForbidden());
        closeStore(staff.token(), staff.password()).andExpect(status().isForbidden());
    }

    @Test
    void staffUsernamesFollowTheSignUpRulesAndAStoreHasAtMostFive() throws Exception {
        Owner owner = owner();
        Staff first = addStaff(owner);

        add(owner.token(), first.username().toUpperCase()).andExpect(status().isConflict());   // taken, whatever the case
        add(owner.token(), "a b").andExpect(status().isBadRequest());
        add(owner.token(), "x").andExpect(status().isBadRequest());
        add(tokenFor(Role.CONSUMER), uniqueName()).andExpect(status().isForbidden());

        for (int i = 0; i < StaffService.MAX_STAFF - 1; i++) {
            add(owner.token(), uniqueName()).andExpect(status().isCreated());
        }
        add(owner.token(), uniqueName()).andExpect(status().isConflict());
        mvc.perform(get("/api/owner/staff").header("Authorization", bearer(owner.token())))
                .andExpect(jsonPath("$", hasSize(StaffService.MAX_STAFF)));
    }

    @Test
    void aNewPasswordOrRemovalLogsTheStaffMemberOut() throws Exception {
        Owner owner = owner();
        Staff staff = addStaff(owner);
        // Another store's owner can't touch them.
        String stranger = owner().token();
        mvc.perform(post("/api/owner/staff/{id}/password", staff.id()).header("Authorization", bearer(stranger)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/owner/staff/{id}", staff.id()).header("Authorization", bearer(stranger)))
                .andExpect(status().isNotFound());

        String replacement = JsonPath.read(mvc.perform(post("/api/owner/staff/{id}/password", staff.id())
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.temporaryPassword");
        mvc.perform(get("/api/owner/items").header("Authorization", bearer(staff.token()))).andExpect(status().isUnauthorized());
        login(staff.username(), staff.password()).andExpect(status().isUnauthorized());
        String newToken = JsonPath.read(login(staff.username(), replacement).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.token");

        mvc.perform(delete("/api/owner/staff/{id}", staff.id()).header("Authorization", bearer(owner.token())))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/owner/items").header("Authorization", bearer(newToken))).andExpect(status().isUnauthorized());
        login(staff.username(), replacement).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/owner/staff").header("Authorization", bearer(owner.token()))).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void closingAStoreTakesItOffTheAppAndDeletesItsAccounts() throws Exception {
        Owner owner = owner();
        Staff staff = addStaff(owner);
        String buyer = tokenFor(Role.CONSUMER);
        String slug = JsonPath.read(mvc.perform(get("/api/owner/store").header("Authorization", bearer(owner.token())))
                .andReturn().getResponse().getContentAsString(), "$.slug");
        String itemId = JsonPath.read(createItem(owner.token()).andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(put("/api/cart/items/{id}", itemId)
                        .header("Authorization", bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 1}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/checkout").header("Authorization", bearer(buyer))).andExpect(status().isCreated());

        closeStore(owner.token(), "not-the-password").andExpect(status().isBadRequest());
        mvc.perform(get("/api/stores/{slug}", slug)).andExpect(status().isOk());

        closeStore(owner.token(), PASSWORD).andExpect(status().isNoContent());

        mvc.perform(get("/api/stores/{slug}", slug)).andExpect(status().isNotFound());
        mvc.perform(get("/api/items/{id}", itemId)).andExpect(status().isNotFound());
        // The owner's and the staff's accounts are gone.
        mvc.perform(get("/api/account").header("Authorization", bearer(owner.token()))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/owner/items").header("Authorization", bearer(staff.token()))).andExpect(status().isUnauthorized());
        login(owner.username(), PASSWORD).andExpect(status().isUnauthorized());
        login(staff.username(), staff.password()).andExpect(status().isUnauthorized());
        // The buyer's pickup is cancelled, not missed, and they're told.
        mvc.perform(get("/api/reservations").header("Authorization", bearer(buyer)))
                .andExpect(jsonPath("$[0].status").value("CANCELLED"));
        mvc.perform(get("/api/alerts").header("Authorization", bearer(buyer)))
                .andExpect(jsonPath("$.alerts[0].kind").value("CANCELLED"));
    }

    // --- helpers ---

    private Owner owner() throws Exception {
        User user = new User(uniqueName(), passwordEncoder.encode(PASSWORD), Role.OWNER);
        user.approve();
        users.save(user);
        stores.createForOwner(user, "Store of " + user.getUsername());
        return new Owner(user.getUsername(), login(user.getUsername()));
    }

    private Staff addStaff(Owner owner) throws Exception {
        String body = add(owner.token(), uniqueName())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.temporaryPassword", matchesPattern("[2-9A-Z]{4}-[2-9A-Z]{4}-[2-9A-Z]{4}")))
                .andReturn().getResponse().getContentAsString();
        String username = JsonPath.read(body, "$.username");
        String password = JsonPath.read(body, "$.temporaryPassword");
        String token = JsonPath.read(login(username, password).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.token");
        return new Staff(JsonPath.read(body, "$.id"), username, password, token);
    }

    private ResultActions add(String token, String username) throws Exception {
        return mvc.perform(post("/api/owner/staff")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"" + username + "\"}"));
    }

    private ResultActions closeStore(String token, String password) throws Exception {
        return mvc.perform(post("/api/account/close-store")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\": \"" + password + "\"}"));
    }

    private ResultActions login(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, password)));
    }

    private ResultActions createItem(String token) throws Exception {
        Instant now = Instant.now();
        return mvc.perform(post("/api/owner/items")
                .header("Authorization", bearer(token))
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
                        """.formatted(now.plus(Duration.ofHours(6)), now, now.plus(Duration.ofHours(3)))));
    }
}
