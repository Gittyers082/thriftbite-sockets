package com.thriftbite.backend.report;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Report a problem": what a buyer can report, and what stops it being misused. */
class ReportTest extends ApiTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void aBuyerReportsAProblemWithTheirOwnPickupOnce() throws Exception {
        String owner = tokenFor(Role.OWNER);
        String buyer = tokenFor(Role.CONSUMER);
        String reservationId = reserve(buyer, createItem(owner));

        report(buyer, """
                {"reservationId": "%s", "kind": "STORE_CLOSED", "details": "  Shutters were down at 6pm.  "}
                """.formatted(reservationId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.kind").value("STORE_CLOSED"))
                .andExpect(jsonPath("$.reservationId").value(reservationId))
                .andExpect(jsonPath("$.storeSlug").value(slugOf(owner)))
                .andExpect(jsonPath("$.details").value("Shutters were down at 6pm."))
                .andExpect(jsonPath("$.reply").value(nullValue()));

        report(buyer, "{\"reservationId\": \"%s\", \"kind\": \"NO_FOOD\"}".formatted(reservationId))
                .andExpect(status().isConflict());
        // Someone else's reservation looks the same as one that doesn't exist.
        String someoneElse = tokenFor(Role.CONSUMER);
        report(someoneElse, "{\"reservationId\": \"%s\", \"kind\": \"NO_FOOD\"}".formatted(reservationId))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/reports").header("Authorization", bearer(buyer))).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/reports").header("Authorization", bearer(someoneElse))).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void aBuyerReportsAStoreAndOneReportStaysOpenAtATime() throws Exception {
        String slug = slugOf(tokenFor(Role.OWNER));
        String buyer = tokenFor(Role.CONSUMER);

        report(buyer, "{\"storeSlug\": \"%s\", \"kind\": \"UNSAFE_FOOD\"}".formatted(slug))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservationId").value(nullValue()))
                .andExpect(jsonPath("$.storeSlug").value(slug));
        report(buyer, "{\"storeSlug\": \"%s\", \"kind\": \"OVERCHARGED\"}".formatted(slug))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("open report")));

        report(buyer, "{\"storeSlug\": \"no-such-store\", \"kind\": \"NO_FOOD\"}").andExpect(status().isNotFound());
    }

    @Test
    void aReportNeedsOneTargetAndSomethingElseNeedsDetails() throws Exception {
        String owner = tokenFor(Role.OWNER);
        String slug = slugOf(owner);
        String buyer = tokenFor(Role.CONSUMER);
        String reservationId = reserve(buyer, createItem(owner));

        report(buyer, "{\"kind\": \"NO_FOOD\"}").andExpect(status().isBadRequest());
        report(buyer, "{\"reservationId\": \"%s\", \"storeSlug\": \"%s\", \"kind\": \"NO_FOOD\"}".formatted(reservationId, slug))
                .andExpect(status().isBadRequest());
        report(buyer, "{\"storeSlug\": \"%s\", \"kind\": \"OTHER\"}".formatted(slug)).andExpect(status().isBadRequest());
        report(buyer, "{\"storeSlug\": \"%s\", \"kind\": \"OTHER\", \"details\": \"bad\"}".formatted(slug))
                .andExpect(status().isBadRequest());
        report(buyer, "{\"storeSlug\": \"%s\", \"kind\": \"REVENGE\"}".formatted(slug)).andExpect(status().isBadRequest());
        report(buyer, "{\"storeSlug\": \"%s\", \"kind\": \"OTHER\", \"details\": \"%s\"}".formatted(slug, "x".repeat(1001)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/reports").header("Authorization", bearer(buyer))).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void onlyBuyersReport() throws Exception {
        String owner = tokenFor(Role.OWNER);
        String body = "{\"storeSlug\": \"%s\", \"kind\": \"NO_FOOD\"}".formatted(slugOf(owner));

        report(owner, body).andExpect(status().isForbidden());
        mvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/reports").header("Authorization", bearer(owner))).andExpect(status().isForbidden());
    }

    @Test
    void aBuyerCanSendFiveReportsADay() throws Exception {
        String buyer = tokenFor(Role.CONSUMER);
        for (int i = 0; i < Reports.MAX_PER_DAY; i++) {
            report(buyer, "{\"storeSlug\": \"%s\", \"kind\": \"NO_FOOD\"}".formatted(slugOf(tokenFor(Role.OWNER))))
                    .andExpect(status().isCreated());
        }

        report(buyer, "{\"storeSlug\": \"%s\", \"kind\": \"NO_FOOD\"}".formatted(slugOf(tokenFor(Role.OWNER))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("report_limit"));
    }

    @Test
    void aPickupFromLongAgoIsTooOldToReport() throws Exception {
        String buyer = tokenFor(Role.CONSUMER);
        String reservationId = reserve(buyer, createItem(tokenFor(Role.OWNER)));
        jdbc.update("UPDATE reservations SET created_at = now() - interval '31 days' WHERE id = ?::uuid", reservationId);

        report(buyer, "{\"reservationId\": \"%s\", \"kind\": \"NO_FOOD\"}".formatted(reservationId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("30 days")));
    }

    @Test
    void deletingTheAccountKeepsTheReportButErasesWhatWasWritten() throws Exception {
        String buyer = tokenFor(Role.CONSUMER);
        String reportId = JsonPath.read(report(buyer, """
                {"storeSlug": "%s", "kind": "OTHER", "details": "My number is 0917 000 0000, call me."}
                """.formatted(slugOf(tokenFor(Role.OWNER))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(post("/api/account/delete")
                        .header("Authorization", bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"" + PASSWORD + "\"}"))
                .andExpect(status().is2xxSuccessful());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reports WHERE id = ?::uuid", Integer.class, reportId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT details FROM reports WHERE id = ?::uuid", String.class, reportId)).isNull();
    }

    // --- helpers ---

    private ResultActions report(String token, String body) throws Exception {
        return mvc.perform(post("/api/reports")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String slugOf(String ownerToken) throws Exception {
        String body = mvc.perform(get("/api/owner/store").header("Authorization", bearer(ownerToken)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.slug");
    }

    private String createItem(String ownerToken) throws Exception {
        Instant now = Instant.now();
        String body = mvc.perform(post("/api/owner/items")
                        .header("Authorization", bearer(ownerToken))
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
                                """.formatted(now.plus(Duration.ofHours(6)), now, now.plus(Duration.ofHours(3)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    /** Reserves one of the item and returns the reservation's id. */
    private String reserve(String buyerToken, String itemId) throws Exception {
        mvc.perform(put("/api/cart/items/{id}", itemId)
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 1}"))
                .andExpect(status().isOk());
        String body = mvc.perform(post("/api/checkout").header("Authorization", bearer(buyerToken)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$[0].id");
    }
}
