package com.thriftbite.backend.demo;

import com.jayway.jsonpath.JsonPath;
import com.thriftbite.backend.ApiTestSupport;
import com.thriftbite.backend.user.Role;
import com.thriftbite.backend.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The public demo: seeded data and one-tap logins. Runs in its own app context and database. */
@TestPropertySource(properties = {
        "app.demo.enabled=true",
        "app.admin.username=boot.admin",
        "app.admin.password=a-long-test-password"
})
class DemoDataTest extends ApiTestSupport {

    @Autowired
    private DemoData demo;

    @Test
    void theDemoListsItsThreeAccounts() throws Exception {
        mvc.perform(get("/api/demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts", hasSize(3)))
                .andExpect(jsonPath("$.resetsAt").exists());
    }

    @Test
    void theDemoOwnerHasTwoPickupsWaiting() throws Exception {
        String token = demoLogin("OWNER");
        mvc.perform(get("/api/owner/reservations").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void theDemoBuyerSeesTheirClaimCodeAndHistory() throws Exception {
        String token = demoLogin("BUYER");
        mvc.perform(get("/api/reservations").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[*].claimCode", hasItem(DemoData.BUYER_CLAIM_CODE)));
    }

    @Test
    void theDemoStoreHasAMonthOfSalesHistory() throws Exception {
        String token = demoLogin("OWNER");
        mvc.perform(get("/api/owner/stats").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storeName").value("Sunrise Bakery"))
                .andExpect(jsonPath("$.days", hasSize(7)))
                .andExpect(jsonPath("$.totals.pickups", greaterThan(0)))
                .andExpect(jsonPath("$.items", hasSize(5)));
    }

    @Test
    void aResetKeepsTheRealAdminAndRebuildsTheDemo() throws Exception {
        demo.reset();
        assertThat(users.existsByUsername("boot.admin")).isTrue();
        assertThat(users.existsByUsername(DemoData.BUYER)).isTrue();
        demoLogin("ADMIN");
    }

    @Test
    void theDemoAdminCanOnlySeeAndApproveTheDemoStores() throws Exception {
        demo.reset();
        String demoAdmin = demoLogin("ADMIN");
        User realOwner = users.save(new User(uniqueName(), passwordEncoder.encode(PASSWORD), Role.OWNER));

        String pending = mvc.perform(get("/api/admin/owners/pending").header("Authorization", bearer(demoAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].username", not(hasItem(realOwner.getUsername()))))
                .andReturn().getResponse().getContentAsString();

        mvc.perform(post("/api/admin/users/{id}/approve", realOwner.getId()).header("Authorization", bearer(demoAdmin)))
                .andExpect(status().isForbidden());
        assertThat(users.findById(realOwner.getId()).orElseThrow().isApproved()).isFalse();

        String demoStore = JsonPath.read(pending, "$[0].id");
        mvc.perform(post("/api/admin/users/{id}/approve", demoStore).header("Authorization", bearer(demoAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approved").value(true));

        // A real admin still sees and approves everyone.
        String realAdmin = tokenFor(Role.ADMIN);
        mvc.perform(get("/api/admin/owners/pending").header("Authorization", bearer(realAdmin)))
                .andExpect(jsonPath("$[*].username", hasItem(realOwner.getUsername())));
        mvc.perform(post("/api/admin/users/{id}/approve", realOwner.getId()).header("Authorization", bearer(realAdmin)))
                .andExpect(status().isOk());
    }

    private String demoLogin(String role) throws Exception {
        String body = mvc.perform(post("/api/demo/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"as\": \"" + role + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }
}
