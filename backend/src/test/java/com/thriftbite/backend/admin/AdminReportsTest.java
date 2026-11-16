package com.thriftbite.backend.admin;

import com.jayway.jsonpath.JsonPath;
import com.thriftbite.backend.ApiTestSupport;
import com.thriftbite.backend.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admins answering problem reports, and the record of what admins did. */
class AdminReportsTest extends ApiTestSupport {

    private static final String REPLY = "We called the store. It closed early that day and has updated its hours.";

    @Test
    void anAdminAnswersAReportAndTheBuyerIsTold() throws Exception {
        String admin = tokenFor(Role.ADMIN);
        String owner = tokenFor(Role.OWNER);
        String buyer = tokenFor(Role.CONSUMER);
        String reportId = report(buyer, slugOf(owner), "Closed at 6pm.");

        String open = mvc.perform(get("/api/admin/reports").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> waiting = JsonPath.read(open, "$[?(@.id == '" + reportId + "')].details");
        assertThat(waiting).containsExactly("Closed at 6pm.");

        resolve(admin, reportId, "  " + REPLY + "  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.reply").value(REPLY));

        // The buyer sees the reply, and gets an alert with it.
        mvc.perform(get("/api/reports").header("Authorization", bearer(buyer)))
                .andExpect(jsonPath("$[0].status").value("RESOLVED"))
                .andExpect(jsonPath("$[0].reply").value(REPLY));
        mvc.perform(get("/api/alerts").header("Authorization", bearer(buyer)))
                .andExpect(jsonPath("$.unread").value(1))
                .andExpect(jsonPath("$.alerts[0].kind").value("REPORT"))
                .andExpect(jsonPath("$.alerts[0].body").value(REPLY));

        // It's off the waiting list, and can't be answered twice.
        String stillOpen = mvc.perform(get("/api/admin/reports").header("Authorization", bearer(admin)))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(stillOpen, "$[?(@.id == '" + reportId + "')].id")).isEmpty();
        resolve(admin, reportId, "Answering it a second time.")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("already answered")));
    }

    @Test
    void onlyAdminsReadAndAnswerReports() throws Exception {
        String owner = tokenFor(Role.OWNER);
        String buyer = tokenFor(Role.CONSUMER);
        String reportId = report(buyer, slugOf(owner), "Closed at 6pm.");

        for (String token : List.of(owner, buyer)) {
            mvc.perform(get("/api/admin/reports").header("Authorization", bearer(token))).andExpect(status().isForbidden());
            mvc.perform(get("/api/admin/activity").header("Authorization", bearer(token))).andExpect(status().isForbidden());
            resolve(token, reportId, REPLY).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/admin/reports")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/reports").header("Authorization", bearer(buyer)))
                .andExpect(jsonPath("$[0].status").value("OPEN"))
                .andExpect(jsonPath("$[0].reply").value(nullValue()));
    }

    @Test
    void aReplyHasToSaySomething() throws Exception {
        String admin = tokenFor(Role.ADMIN);
        String reportId = report(tokenFor(Role.CONSUMER), slugOf(tokenFor(Role.OWNER)), "Closed at 6pm.");

        resolve(admin, reportId, "").andExpect(status().isBadRequest());
        resolve(admin, reportId, "ok").andExpect(status().isBadRequest());
        resolve(admin, reportId, "x".repeat(501)).andExpect(status().isBadRequest());
        resolve(admin, "00000000-0000-0000-0000-000000000000", REPLY).andExpect(status().isNotFound());
    }

    @Test
    void whatAdminsDoIsWrittenDown() throws Exception {
        String admin = tokenFor(Role.ADMIN);
        String owner = tokenFor(Role.OWNER);
        String storeName = JsonPath.read(mvc.perform(get("/api/owner/store").header("Authorization", bearer(owner)))
                .andReturn().getResponse().getContentAsString(), "$.name");
        String reportId = report(tokenFor(Role.CONSUMER), slugOf(owner), "Closed at 6pm.");
        resolve(admin, reportId, REPLY).andExpect(status().isOk());

        mvc.perform(get("/api/admin/activity").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("ANSWER_REPORT"))        // newest first
                .andExpect(jsonPath("$[0].subject", containsString("Report about " + storeName + " from ")))
                .andExpect(jsonPath("$[0].note").value(REPLY));

        // A refused answer leaves no line.
        resolve(admin, reportId, "Answering it a second time.").andExpect(status().isConflict());
        mvc.perform(get("/api/admin/activity").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$[0].note").value(REPLY));
    }

    // --- helpers ---

    private ResultActions resolve(String token, String reportId, String reply) throws Exception {
        return mvc.perform(post("/api/admin/reports/{id}/resolve", reportId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reply\": \"" + reply + "\"}"));
    }

    private String slugOf(String ownerToken) throws Exception {
        String body = mvc.perform(get("/api/owner/store").header("Authorization", bearer(ownerToken)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.slug");
    }

    /** A buyer reports a store in general; returns the report's id. */
    private String report(String buyerToken, String storeSlug, String details) throws Exception {
        String body = mvc.perform(post("/api/reports")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeSlug\": \"%s\", \"kind\": \"STORE_CLOSED\", \"details\": \"%s\"}".formatted(storeSlug, details)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }
}
