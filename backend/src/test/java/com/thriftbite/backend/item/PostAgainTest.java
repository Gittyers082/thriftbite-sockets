package com.thriftbite.backend.item;

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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Post again": a store's usual listings from its history, and posting several at once. */
class PostAgainTest extends ApiTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void lastWeeksListingComesBackWithHowItWent() throws Exception {
        String owner = tokenFor(Role.OWNER);
        String pandesal = createItem(owner, "Pandesal", 4);
        moveBackAWeek(pandesal);   // listed 4 a week ago, none picked up

        mvc.perform(get("/api/owner/items/usual").header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Pandesal"))
                .andExpect(jsonPath("$[0].price").value(20.00))
                .andExpect(jsonPath("$[0].sameWeekday").value(true))
                .andExpect(jsonPath("$[0].lastListed").value(4))
                .andExpect(jsonPath("$[0].lastSold").value(0))
                .andExpect(jsonPath("$[0].suggestedQuantity").value(1))   // most of it was left over
                .andExpect(jsonPath("$[0].alreadyListed").value(false));

        // Once it's posted for today, it's marked so it isn't posted twice.
        createItem(owner, "pandesal", 4);
        mvc.perform(get("/api/owner/items/usual").header("Authorization", bearer(owner)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].alreadyListed").value(true));
    }

    @Test
    void foodStillOnSaleTodayIsNotHistoryYet() throws Exception {
        String owner = tokenFor(Role.OWNER);
        createItem(owner, "Ensaymada", 3);

        mvc.perform(get("/api/owner/items/usual").header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void youCanOnlyPostForTodayOrTomorrow() throws Exception {
        String owner = tokenFor(Role.OWNER);
        LocalDate today = LocalDate.now(UsualListings.ZONE);

        usualFor(owner, today.plusDays(1)).andExpect(status().isOk());
        usualFor(owner, today.plusDays(2)).andExpect(status().isBadRequest());
        usualFor(owner, today.minusDays(1)).andExpect(status().isBadRequest());
    }

    @Test
    void aBatchPostsEveryListingOrNone() throws Exception {
        String owner = tokenFor(Role.OWNER);

        postBatch(owner, entry("Pandesal", "20.00", 6), entry("Spanish bread", "30.00", 4))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].quantity").value(6))
                .andExpect(jsonPath("$[1].status").value("AVAILABLE"));
        assertThat(listingsOf(owner)).isEqualTo(2);

        // The second one costs more than its usual price, so neither is posted.
        postBatch(owner, entry("Cheese rolls", "20.00", 2), entry("Monay", "99.00", 2))
                .andExpect(status().isBadRequest());
        assertThat(listingsOf(owner)).isEqualTo(2);

        postBatch(owner).andExpect(status().isBadRequest());   // nothing to post
        postBatch(tokenFor(Role.CONSUMER), entry("Pandesal", "20.00", 1)).andExpect(status().isForbidden());
    }

    // --- helpers ---

    private static String entry(String name, String price, int quantity) {
        return """
                {"name": "%s", "price": %s, "originalPrice": 40.00, "quantity": %d, "pickupLocation": "Bonifacio St, Baguio"}
                """.formatted(name, price, quantity);
    }

    private ResultActions postBatch(String token, String... entries) throws Exception {
        Instant now = Instant.now();
        return mvc.perform(post("/api/owner/items/batch")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"pickupStart": "%s", "pickupEnd": "%s", "items": [%s]}
                        """.formatted(now.plus(Duration.ofHours(1)), now.plus(Duration.ofHours(3)), String.join(",", entries))));
    }

    private ResultActions usualFor(String ownerToken, LocalDate day) throws Exception {
        return mvc.perform(get("/api/owner/items/usual").param("for", day.toString())
                .header("Authorization", bearer(ownerToken)));
    }

    private int listingsOf(String ownerToken) throws Exception {
        String body = mvc.perform(get("/api/owner/items").header("Authorization", bearer(ownerToken)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<List<Object>>read(body, "$").size();
    }

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
                                """.formatted(name, quantity,
                                now.plus(Duration.ofHours(3)), now, now.plus(Duration.ofHours(3)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    /** Makes a listing look like it was posted, and ended, exactly seven days ago. */
    private void moveBackAWeek(String itemId) {
        UUID id = UUID.fromString(itemId);
        jdbc.update("""
                UPDATE items
                   SET pickup_start = pickup_start - interval '7 days',
                       pickup_end   = pickup_end   - interval '7 days',
                       expires_at   = expires_at   - interval '7 days'
                 WHERE id = ?
                """, id);
        jdbc.update("UPDATE item_events SET occurred_at = occurred_at - interval '7 days' WHERE item_id = ?", id);
    }
}
