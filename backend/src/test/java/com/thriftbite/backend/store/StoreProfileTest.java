package com.thriftbite.backend.store;

import com.jayway.jsonpath.JsonPath;
import com.thriftbite.backend.ApiTestSupport;
import com.thriftbite.backend.user.Role;
import com.thriftbite.backend.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The owner's store profile, and the public store page built from it. */
class StoreProfileTest extends ApiTestSupport {

    private static final String PROFILE = """
            {
              "name": "Sunrise Bakery",
              "address": "112 Session Road",
              "barangay": "Session Road Area",
              "landmark": "  Green awning  ",
              "gcashAccepted": true,
              "defaultPickupStart": "17:00",
              "defaultPickupEnd": "19:00",
              "hours": [
                {"weekday": "MONDAY", "opens": "07:00", "closes": "19:00"},
                {"weekday": "FRIDAY", "opens": "07:00", "closes": "20:00"}
              ],
              "version": %d
            }
            """;

    @Test
    void theOwnerFillsInTheProfileAndBuyersSeeItOnTheStorePage() throws Exception {
        String owner = tokenFor(Role.OWNER);
        String mine = mvc.perform(get("/api/owner/store").header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").value(nullValue()))
                .andExpect(jsonPath("$.hours", hasSize(0)))
                .andReturn().getResponse().getContentAsString();
        String slug = JsonPath.read(mine, "$.slug");
        int version = JsonPath.read(mine, "$.version");

        save(owner, PROFILE.formatted(version))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sunrise Bakery"))
                .andExpect(jsonPath("$.landmark").value("Green awning"))   // trimmed
                .andExpect(jsonPath("$.slug").value(slug));                 // renaming keeps the link

        // no login needed
        mvc.perform(get("/api/stores/{slug}", slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.store.address").value("112 Session Road"))
                .andExpect(jsonPath("$.store.gcashAccepted").value(true))
                .andExpect(jsonPath("$.store.defaultPickupStart").value("17:00:00"))
                .andExpect(jsonPath("$.store.hours", hasSize(2)))
                .andExpect(jsonPath("$.store.hours[1].weekday").value("FRIDAY"))
                .andExpect(jsonPath("$.store.hours[1].closes").value("20:00:00"))
                .andExpect(jsonPath("$.items", hasSize(0)))
                // a new store has no track record, and the page doesn't invent one
                .andExpect(jsonPath("$.stats.mealsRescued").value(0))
                .andExpect(jsonPath("$.stats.pickupRate").value(nullValue()))
                .andExpect(jsonPath("$.stats.usualPostTime").value(nullValue()));
    }

    @Test
    void savingFromAStaleCopyIsRefused() throws Exception {
        String owner = tokenFor(Role.OWNER);
        int version = versionOf(owner);
        save(owner, PROFILE.formatted(version)).andExpect(status().isOk());

        // a second tab still holding the old version
        save(owner, PROFILE.formatted(version)).andExpect(status().isConflict());
    }

    @Test
    void hoursThatDontMakeSenseAreRefused() throws Exception {
        String owner = tokenFor(Role.OWNER);
        int version = versionOf(owner);
        String closesBeforeItOpens = PROFILE.formatted(version).replace("\"closes\": \"19:00\"", "\"closes\": \"06:00\"");
        String sameDayTwice = PROFILE.formatted(version).replace("FRIDAY", "MONDAY");
        String pickupBackwards = PROFILE.formatted(version).replace("\"defaultPickupEnd\": \"19:00\"", "\"defaultPickupEnd\": \"16:00\"");

        save(owner, closesBeforeItOpens).andExpect(status().isBadRequest());
        save(owner, sameDayTwice).andExpect(status().isBadRequest());
        save(owner, pickupBackwards).andExpect(status().isBadRequest());
        save(owner, PROFILE.formatted(version).replace("Sunrise Bakery", " ")).andExpect(status().isBadRequest());
    }

    @Test
    void onlyStoresHaveAProfile() throws Exception {
        mvc.perform(get("/api/owner/store").header("Authorization", bearer(tokenFor(Role.CONSUMER))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/owner/store")).andExpect(status().isUnauthorized());
    }

    /** Until an admin approves the owner, the store has no public page. */
    @Test
    void aStoreWaitingForApprovalHasNoPublicPage() throws Exception {
        User pending = users.save(new User(uniqueName(), passwordEncoder.encode(PASSWORD), Role.OWNER));
        Store store = stores.createForOwner(pending, "Not Yet Open " + uniqueName());

        mvc.perform(get("/api/stores/{slug}", store.getSlug())).andExpect(status().isNotFound());

        pending.approve();
        users.save(pending);
        mvc.perform(get("/api/stores/{slug}", store.getSlug())).andExpect(status().isOk());
        mvc.perform(get("/api/stores/{slug}", "no-such-store")).andExpect(status().isNotFound());
    }

    // --- helpers ---

    private ResultActions save(String ownerToken, String body) throws Exception {
        return mvc.perform(put("/api/owner/store")
                .header("Authorization", bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private int versionOf(String ownerToken) throws Exception {
        String mine = mvc.perform(get("/api/owner/store").header("Authorization", bearer(ownerToken)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(mine, "$.version");
    }
}
