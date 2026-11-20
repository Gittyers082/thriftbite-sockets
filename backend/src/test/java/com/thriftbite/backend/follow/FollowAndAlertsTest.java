package com.thriftbite.backend.follow;

import com.jayway.jsonpath.JsonPath;
import com.thriftbite.backend.ApiTestSupport;
import com.thriftbite.backend.store.Store;
import com.thriftbite.backend.user.Role;
import com.thriftbite.backend.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Following a store, and the alerts buyers get. */
class FollowAndAlertsTest extends ApiTestSupport {

    @Test
    void aFollowerGetsOneAlertThatGrowsWhenTheStorePostsAgain() throws Exception {
        String owner = tokenFor(Role.OWNER);
        String slug = slugOf(owner);
        String follower = tokenFor(Role.CONSUMER);
        String quietFollower = tokenFor(Role.CONSUMER);
        String stranger = tokenFor(Role.CONSUMER);
        follow(follower, slug, "").andExpect(status().isOk()).andExpect(jsonPath("$.alertsOn").value(true));
        follow(quietFollower, slug, "{\"alertsOn\": false}").andExpect(jsonPath("$.alertsOn").value(false));

        createItem(owner, "Pandesal", 5);

        alerts(follower)
                .andExpect(jsonPath("$.unread").value(1))
                .andExpect(jsonPath("$.alerts", hasSize(1)))
                .andExpect(jsonPath("$.alerts[0].kind").value("DEAL"))
                .andExpect(jsonPath("$.alerts[0].title", endsWith("posted a deal")))
                .andExpect(jsonPath("$.alerts[0].body").value("Pandesal, from ₱20.00."))
                .andExpect(jsonPath("$.alerts[0].link").value("/stores/" + slug));
        alerts(quietFollower).andExpect(jsonPath("$.alerts", hasSize(0)));   // following, alerts off
        alerts(stranger).andExpect(jsonPath("$.alerts", hasSize(0)));

        // Read it, then the store posts again the same day: the same alert, rewritten and unread again.
        mvc.perform(post("/api/alerts/read").header("Authorization", bearer(follower))).andExpect(status().isNoContent());
        createItem(owner, "Ensaymada", 2);

        alerts(follower)
                .andExpect(jsonPath("$.unread").value(1))
                .andExpect(jsonPath("$.alerts", hasSize(1)))
                .andExpect(jsonPath("$.alerts[0].title", endsWith("posted 2 deals")))
                .andExpect(jsonPath("$.alerts[0].body").value("Pandesal and Ensaymada, from ₱20.00."));
    }

    @Test
    void aReservationLeavesAReminderThatIsReplacedWhenTheFoodIsPickedUp() throws Exception {
        String owner = tokenFor(Role.OWNER);
        String buyer = tokenFor(Role.CONSUMER);
        String itemId = createItem(owner, "Pandesal", 5);

        String reservation = reserve(buyer, itemId, 2);
        String reservationId = JsonPath.read(reservation, "$[0].id");
        String claimCode = JsonPath.read(reservation, "$[0].claimCode");
        String listed = alerts(buyer)
                .andExpect(jsonPath("$.alerts", hasSize(1)))
                .andExpect(jsonPath("$.alerts[0].kind").value("PICKUP"))
                .andExpect(jsonPath("$.alerts[0].title", startsWith("Pick up at ")))
                .andExpect(jsonPath("$.alerts[0].body").value("Show your claim code at the counter and pay ₱40.00."))
                .andExpect(jsonPath("$.alerts[0].link").value("/pickup/" + reservationId))
                .andReturn().getResponse().getContentAsString();
        // The code is the proof at the counter; it isn't copied anywhere else.
        org.assertj.core.api.Assertions.assertThat(listed).doesNotContain(claimCode);

        mvc.perform(post("/api/owner/reservations/{id}/complete", reservationId)
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"claimCode\": \"" + claimCode + "\"}"))
                .andExpect(status().isOk());

        alerts(buyer)
                .andExpect(jsonPath("$.alerts", hasSize(1)))
                .andExpect(jsonPath("$.alerts[0].kind").value("PICKED_UP"))
                .andExpect(jsonPath("$.alerts[0].body").value("You saved ₱40.00 on Pandesal."));
    }

    @Test
    void cancellingRemovesTheReminder() throws Exception {
        String buyer = tokenFor(Role.CONSUMER);
        String reservation = reserve(buyer, createItem(tokenFor(Role.OWNER), "Pandesal", 5), 1);
        alerts(buyer).andExpect(jsonPath("$.alerts", hasSize(1)));

        mvc.perform(post("/api/reservations/{id}/cancel", JsonPath.<String>read(reservation, "$[0].id"))
                        .header("Authorization", bearer(buyer)))
                .andExpect(status().isOk());

        alerts(buyer).andExpect(jsonPath("$.alerts", hasSize(0))).andExpect(jsonPath("$.unread").value(0));
    }

    @Test
    void theStorePageSaysWhetherTheBuyerFollowsIt() throws Exception {
        String owner = tokenFor(Role.OWNER);
        String slug = slugOf(owner);
        String buyer = tokenFor(Role.CONSUMER);

        mvc.perform(get("/api/stores/{slug}", slug).header("Authorization", bearer(buyer)))
                .andExpect(jsonPath("$.viewer.following").value(false))
                .andExpect(jsonPath("$.stats.followers").value(0));
        follow(buyer, slug, "").andExpect(status().isOk());
        follow(buyer, slug, "").andExpect(status().isOk());   // twice is the same as once

        mvc.perform(get("/api/stores/{slug}", slug).header("Authorization", bearer(buyer)))
                .andExpect(jsonPath("$.viewer.following").value(true))
                .andExpect(jsonPath("$.stats.followers").value(1));
        mvc.perform(get("/api/follows").header("Authorization", bearer(buyer)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].slug").value(slug));
        // Visitors and the store itself aren't "viewers".
        mvc.perform(get("/api/stores/{slug}", slug)).andExpect(jsonPath("$.viewer").value(nullValue()));
        mvc.perform(get("/api/stores/{slug}", slug).header("Authorization", bearer(owner)))
                .andExpect(jsonPath("$.viewer").value(nullValue()));

        mvc.perform(delete("/api/stores/{slug}/follow", slug).header("Authorization", bearer(buyer)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/follows").header("Authorization", bearer(buyer))).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void onlyBuyersFollowAndOnlyStoresThatArePublic() throws Exception {
        String owner = tokenFor(Role.OWNER);
        String slug = slugOf(owner);
        follow(owner, slug, "").andExpect(status().isForbidden());
        mvc.perform(put("/api/stores/{slug}/follow", slug)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/alerts").header("Authorization", bearer(owner))).andExpect(status().isForbidden());

        String buyer = tokenFor(Role.CONSUMER);
        follow(buyer, "no-such-store", "").andExpect(status().isNotFound());
        User pending = users.save(new User(uniqueName(), passwordEncoder.encode(PASSWORD), Role.OWNER));
        Store notApprovedYet = stores.createForOwner(pending, "Pending " + uniqueName());
        follow(buyer, notApprovedYet.getSlug(), "").andExpect(status().isNotFound());
    }

    @Test
    void alertsBelongToOneBuyer() throws Exception {
        String buyer = tokenFor(Role.CONSUMER);
        reserve(buyer, createItem(tokenFor(Role.OWNER), "Pandesal", 5), 1);
        String alertId = JsonPath.read(alerts(buyer).andReturn().getResponse().getContentAsString(), "$.alerts[0].id");

        // Someone else marking it read does nothing.
        String someoneElse = tokenFor(Role.CONSUMER);
        mvc.perform(post("/api/alerts/{id}/read", alertId).header("Authorization", bearer(someoneElse)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/alerts/count").header("Authorization", bearer(buyer)))
                .andExpect(jsonPath("$.unread").value(1));

        mvc.perform(post("/api/alerts/{id}/read", alertId).header("Authorization", bearer(buyer)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/alerts/count").header("Authorization", bearer(buyer)))
                .andExpect(jsonPath("$.unread").value(0));
    }

    // --- helpers ---

    private String slugOf(String ownerToken) throws Exception {
        String body = mvc.perform(get("/api/owner/store").header("Authorization", bearer(ownerToken)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.slug");
    }

    /** @param body "" for a plain follow, or JSON such as {"alertsOn": false} */
    private ResultActions follow(String token, String slug, String body) throws Exception {
        var request = put("/api/stores/{slug}/follow", slug).header("Authorization", bearer(token));
        return mvc.perform(body.isEmpty() ? request : request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions alerts(String token) throws Exception {
        return mvc.perform(get("/api/alerts").header("Authorization", bearer(token))).andExpect(status().isOk());
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
                                """.formatted(name, quantity, now.plus(Duration.ofHours(6)), now, now.plus(Duration.ofHours(3)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

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
}
