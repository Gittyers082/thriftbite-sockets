package com.thriftbite.backend.store;

import com.jayway.jsonpath.JsonPath;
import com.thriftbite.backend.ApiTestSupport;
import com.thriftbite.backend.common.ExpiryJob;
import com.thriftbite.backend.item.Item;
import com.thriftbite.backend.item.ItemRepository;
import com.thriftbite.backend.user.Role;
import com.thriftbite.backend.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StoresAndStockHistoryTest extends ApiTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ItemRepository items;

    @Autowired
    private ExpiryJob expiryJob;

    @Test
    void signingUpAsAnOwnerCreatesTheStore() throws Exception {
        String body = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "password": "%s", "accountType": "OWNER", "storeName": "Hillside Café"}
                                """.formatted(uniqueName(), PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID userId = UUID.fromString(JsonPath.read(body, "$.id"));

        Map<String, Object> store = jdbc.queryForMap("""
                SELECT s.name, s.slug, m.role FROM stores s JOIN store_members m ON m.store_id = s.id
                 WHERE m.user_id = ?
                """, userId);
        assertThat(store.get("name")).isEqualTo("Hillside Café");
        assertThat((String) store.get("slug")).startsWith("hillside-cafe");
        assertThat(store.get("role")).isEqualTo("OWNER");
    }

    @Test
    void slugsAreReadableAndSafe() {
        assertThat(StoreService.slugify("Lola Ising's Carinderia")).isEqualTo("lola-isings-carinderia");
        assertThat(StoreService.slugify("  Pine Grove Café!! ")).isEqualTo("pine-grove-cafe");
        assertThat(StoreService.slugify("???")).isEqualTo("store");
    }

    @Test
    void listingsShowTheirStore() throws Exception {
        String owner = tokenFor(Role.OWNER);
        String item = createItem(owner, 3);
        mvc.perform(get("/api/items/{id}", item))
                .andExpect(jsonPath("$.storeName").exists())
                .andExpect(jsonPath("$.storeSlug").exists());
    }

    @Test
    void everyStockChangeIsRecordedWithTheStockAfterIt() throws Exception {
        String owner = tokenFor(Role.OWNER);
        String buyer = tokenFor(Role.CONSUMER);
        String item = createItem(owner, 2);

        mvc.perform(put("/api/cart/items/{id}", item).header("Authorization", bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\": 2}"))
                .andExpect(status().isOk());
        String reservationId = JsonPath.read(mvc.perform(post("/api/checkout").header("Authorization", bearer(buyer)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$[0].id");
        mvc.perform(post("/api/reservations/{id}/cancel", reservationId).header("Authorization", bearer(buyer)))
                .andExpect(status().isOk());

        // the owner bakes 3 more
        String current = mvc.perform(get("/api/items/{id}", item)).andReturn().getResponse().getContentAsString();
        Integer version = JsonPath.read(current, "$.version");
        mvc.perform(put("/api/owner/items/{id}", item).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(itemJson(5, version)))
                .andExpect(status().isOk());

        List<Map<String, Object>> history = jdbc.queryForList(
                "SELECT type, qty_delta, qty_after, reservation_id FROM item_events WHERE item_id = ? ORDER BY id",
                UUID.fromString(item));
        assertThat(history).extracting(row -> row.get("type"))
                .containsExactly("LISTED", "RESERVED", "SOLD_OUT", "RELEASED", "ADJUSTED");
        assertThat(history).extracting(row -> row.get("qty_after")).containsExactly(2, 0, 0, 2, 5);
        assertThat(history).extracting(row -> row.get("qty_delta")).containsExactly(2, -2, 0, 2, 3);
        assertThat(history.get(1).get("reservation_id")).isEqualTo(UUID.fromString(reservationId));
    }

    @Test
    void expiryRecordsWhatWasLeftOver() {
        User owner = users.save(new User(uniqueName(), "not-a-real-hash", Role.OWNER));
        Store store = stores.createForOwner(owner, "Leftover Bakery");
        Instant now = Instant.now();
        Item stale = items.save(new Item(store, owner, "Monay", null, new BigDecimal("20.00"), new BigDecimal("40.00"),
                3, now.minus(Duration.ofMinutes(1)), "Baguio",
                now.minus(Duration.ofHours(3)), now.minus(Duration.ofHours(2))));

        expiryJob.run();

        Map<String, Object> event = jdbc.queryForMap(
                "SELECT type, qty_after FROM item_events WHERE item_id = ?", stale.getId());
        assertThat(event.get("type")).isEqualTo("EXPIRED");
        assertThat(event.get("qty_after")).isEqualTo(3);
    }

    @Test
    void anOwnerOnlySeesTheirOwnStoresPickups() throws Exception {
        String owner = tokenFor(Role.OWNER);
        String otherOwner = tokenFor(Role.OWNER);
        String buyer = tokenFor(Role.CONSUMER);
        String item = createItem(owner, 2);
        mvc.perform(put("/api/cart/items/{id}", item).header("Authorization", bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\": 1}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/checkout").header("Authorization", bearer(buyer)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].storeName").exists());

        mvc.perform(get("/api/owner/reservations").header("Authorization", bearer(owner)))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/owner/reservations").header("Authorization", bearer(otherOwner)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    // --- helpers ---

    private String createItem(String ownerToken, int quantity) throws Exception {
        String body = mvc.perform(post("/api/owner/items")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemJson(quantity, null)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private static String itemJson(int quantity, Integer version) {
        Instant now = Instant.now();
        return """
                {
                  "name": "Ensaymada",
                  "price": 30.00,
                  "originalPrice": 60.00,
                  "quantity": %d,
                  "expiresAt": "%s",
                  "pickupLocation": "Session Road, Baguio",
                  "pickupStart": "%s",
                  "pickupEnd": "%s",
                  "version": %s
                }
                """.formatted(quantity, now.plus(Duration.ofHours(6)), now, now.plus(Duration.ofHours(3)),
                version == null ? "null" : version.toString());
    }
}
