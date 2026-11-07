package com.thriftbite.backend.item;

import com.jayway.jsonpath.JsonPath;
import com.thriftbite.backend.ApiTestSupport;
import com.thriftbite.backend.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ItemApiTest extends ApiTestSupport {

    @Test
    void ownerListsFoodAndAnyoneCanBrowseItWithoutLoggingIn() throws Exception {
        String owner = tokenFor(Role.OWNER);
        String name = "Ensaymada " + tag();

        create(owner, itemJson(name, "40.00", "80.00", 3, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.discountPercent").value(50))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));

        // no Authorization header: browsing is public
        mvc.perform(get("/api/items").param("q", name))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value(name));
    }

    @Test
    void searchTreatsPercentSignsAsText() throws Exception {
        String owner = tokenFor(Role.OWNER);
        String tag = tag();
        create(owner, itemJson("Pandesal " + tag, "10.00", "20.00", 5, null)).andExpect(status().isCreated());

        // "%" would match everything if it weren't escaped
        mvc.perform(get("/api/items").param("q", tag + "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void consumersCannotCreateListings() throws Exception {
        String consumer = tokenFor(Role.CONSUMER);
        create(consumer, itemJson("Not allowed " + tag(), "10.00", "20.00", 1, null))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownersCannotEditSomeoneElsesListing() throws Exception {
        String alice = tokenFor(Role.OWNER);
        String bob = tokenFor(Role.OWNER);
        String id = idOf(create(alice, itemJson("Alice's bread " + tag(), "10.00", "20.00", 2, null)));

        mvc.perform(put("/api/owner/items/{id}", id)
                        .header("Authorization", bearer(bob))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemJson("Hijacked", "1.00", "20.00", 2, 0L)))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/owner/items/{id}", id).header("Authorization", bearer(bob)))
                .andExpect(status().isForbidden());
    }

    @Test
    void businessRulesAreEnforced() throws Exception {
        String owner = tokenFor(Role.OWNER);

        create(owner, itemJson("Too pricey " + tag(), "90.00", "50.00", 1, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("price can't be higher than the original price"));
        create(owner, itemJson("Nothing to sell " + tag(), "10.00", "20.00", 0, null))
                .andExpect(status().isBadRequest());
        create(owner, itemJson("", "10.00", "20.00", 1, null))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatingWithAStaleVersionIsRejected() throws Exception {
        String owner = tokenFor(Role.OWNER);
        String name = "Puto " + tag();
        String id = idOf(create(owner, itemJson(name, "15.00", "30.00", 4, null)));

        // first edit with the current version works and bumps the version to 1
        mvc.perform(put("/api/owner/items/{id}", id)
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemJson(name, "12.00", "30.00", 4, 0L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        // a second edit that still thinks the version is 0 must not overwrite the first
        mvc.perform(put("/api/owner/items/{id}", id)
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemJson(name, "5.00", "30.00", 4, 0L)))
                .andExpect(status().isConflict());
    }

    @Test
    void settingStockToZeroMarksItSoldOut() throws Exception {
        String owner = tokenFor(Role.OWNER);
        String name = "Kakanin " + tag();
        String id = idOf(create(owner, itemJson(name, "15.00", "30.00", 2, null)));

        mvc.perform(put("/api/owner/items/{id}", id)
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemJson(name, "15.00", "30.00", 0, 0L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SOLD_OUT"));

        mvc.perform(get("/api/items").param("q", name))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void removedItemsDisappearEverywhere() throws Exception {
        String owner = tokenFor(Role.OWNER);
        String name = "Leftover rice " + tag();
        String id = idOf(create(owner, itemJson(name, "5.00", "10.00", 1, null)));

        mvc.perform(delete("/api/owner/items/{id}", id).header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/items/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/items").param("q", name)).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/owner/items").header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(id))));
    }

    // --- helpers ---

    private ResultActions create(String token, String json) throws Exception {
        return mvc.perform(post("/api/owner/items")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private static String idOf(ResultActions created) throws Exception {
        return JsonPath.read(created.andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    /** A valid listing: pickup in the next 3 hours, expires in 6. */
    private static String itemJson(String name, String price, String originalPrice, int quantity, Long version) {
        Instant now = Instant.now();
        return """
                {
                  "name": "%s",
                  "description": "Baked today",
                  "price": %s,
                  "originalPrice": %s,
                  "quantity": %d,
                  "expiresAt": "%s",
                  "pickupLocation": "Session Road, Baguio",
                  "pickupStart": "%s",
                  "pickupEnd": "%s",
                  "version": %s
                }
                """.formatted(name, price, originalPrice, quantity,
                now.plus(Duration.ofHours(6)), now, now.plus(Duration.ofHours(3)),
                version == null ? "null" : version.toString());
    }

    private static String tag() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
