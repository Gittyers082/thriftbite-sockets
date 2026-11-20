package com.thriftbite.backend.live;

import com.jayway.jsonpath.JsonPath;
import com.thriftbite.backend.ApiTestSupport;
import com.thriftbite.backend.user.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Live updates over a real WebSocket. The app listens on a real port here
 * (the other API tests don't need one), and the test connects like a browser would.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LiveUpdatesTest extends ApiTestSupport {

    /** The frontend's address in local development, which the app allows by default. */
    private static final String OUR_SITE = "http://localhost:5173";

    @Value("${local.server.port}")
    private int port;

    private final List<LiveClient> sockets = new ArrayList<>();

    @AfterEach
    void closeSockets() {
        sockets.forEach(LiveClient::close);
    }

    @Test
    void everyoneSeesTheStockDropAfterACheckout() throws Exception {
        LiveClient visitor = connect();   // not logged in, like someone on the browse page
        String itemId = createItem(tokenFor(Role.OWNER), 5);
        visitor.await(item(itemId, 5, "AVAILABLE"));

        String buyer = tokenFor(Role.CONSUMER);
        addToCart(buyer, itemId, 2).andExpect(status().isOk());
        checkout(buyer).andExpect(status().isCreated());

        visitor.await(item(itemId, 3, "AVAILABLE"));
    }

    /** The whole point of sending after commit: a rolled-back change must never be announced. */
    @Test
    void aCheckoutThatRollsBackSendsNothing() throws Exception {
        LiveClient visitor = connect();
        String owner = tokenFor(Role.OWNER);
        String bread = createItem(owner, 5);
        String lastRoll = createItem(owner, 1);

        String slowBuyer = tokenFor(Role.CONSUMER);
        addToCart(slowBuyer, bread, 2).andExpect(status().isOk());
        addToCart(slowBuyer, lastRoll, 1).andExpect(status().isOk());

        String fastBuyer = tokenFor(Role.CONSUMER);
        addToCart(fastBuyer, lastRoll, 1).andExpect(status().isOk());
        checkout(fastBuyer).andExpect(status().isCreated());
        visitor.await(item(lastRoll, 0, "SOLD_OUT"));
        visitor.collect(300);   // forget everything so far

        // Takes 2 bread, then fails on the roll, and the bread goes back.
        checkout(slowBuyer).andExpect(status().isConflict());

        assertThat(visitor.collect(800)).isEmpty();
    }

    @Test
    void onlyTheStoreAndTheBuyerHearAboutAReservation() throws Exception {
        String owner = tokenFor(Role.OWNER);
        String buyer = tokenFor(Role.CONSUMER);
        String itemId = createItem(owner, 5);

        LiveClient store = connect().logIn(owner);
        LiveClient buyerScreen = connect().logIn(buyer);
        LiveClient otherStore = connect().logIn(tokenFor(Role.OWNER));
        LiveClient visitor = connect();

        addToCart(buyer, itemId, 1).andExpect(status().isOk());
        checkout(buyer).andExpect(status().isCreated());

        store.await("\"store\"");
        buyerScreen.await("\"reservations\"");
        // Both of the others hear the public stock count, and nothing else.
        assertThat(otherStore.collect(800)).isNotEmpty().allMatch(message -> message.contains("\"item\""));
        assertThat(visitor.collect(0)).isNotEmpty().allMatch(message -> message.contains("\"item\""));
    }

    @Test
    void messagesNeverCarryPrivateDetails() throws Exception {
        String owner = tokenFor(Role.OWNER);
        String buyer = tokenFor(Role.CONSUMER);
        String itemId = createItem(owner, 5);
        LiveClient store = connect().logIn(owner);

        addToCart(buyer, itemId, 1).andExpect(status().isOk());
        String reservation = checkout(buyer).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String claimCode = JsonPath.read(reservation, "$[0].claimCode");
        String buyerName = JsonPath.read(reservation, "$[0].buyerName");

        store.await("\"store\"");
        // Even the store's own socket only gets a hint; the details come from the API.
        assertThat(store.collect(500)).noneMatch(m -> m.contains(claimCode) || m.contains(buyerName));
    }

    @Test
    void aBadTokenDoesNotLogTheSocketIn() throws Exception {
        LiveClient socket = connect();
        socket.send("{\"type\":\"auth\",\"token\":\"not-a-real-token\"}");
        socket.await("\"auth_failed\"");
    }

    @Test
    void anotherWebsiteCannotOpenAConnection() {
        assertThatThrownBy(() -> LiveClient.connect(port, "https://evil.example"))
                .isInstanceOf(ExecutionException.class);
    }

    // --- helpers ---

    private LiveClient connect() throws Exception {
        LiveClient client = LiveClient.connect(port, OUR_SITE);
        sockets.add(client);
        return client;
    }

    /** The message everyone gets when a listing's stock changes. */
    private static String item(String itemId, int quantity, String status) {
        return "{\"type\":\"item\",\"id\":\"%s\",\"quantity\":%d,\"status\":\"%s\"}".formatted(itemId, quantity, status);
    }

    private String createItem(String ownerToken, int quantity) throws Exception {
        Instant now = Instant.now();
        String body = mvc.perform(post("/api/owner/items")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Pandesal %s",
                                  "price": 20.00,
                                  "originalPrice": 40.00,
                                  "quantity": %d,
                                  "expiresAt": "%s",
                                  "pickupLocation": "Bonifacio St, Baguio",
                                  "pickupStart": "%s",
                                  "pickupEnd": "%s"
                                }
                                """.formatted(UUID.randomUUID().toString().substring(0, 6), quantity,
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
}
