package com.thriftbite.backend;

import com.jayway.jsonpath.JsonPath;
import com.thriftbite.backend.store.StoreService;
import com.thriftbite.backend.user.Role;
import com.thriftbite.backend.user.User;
import com.thriftbite.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared setup for API tests: a real app, a real Postgres (Testcontainers),
 * and helpers to create users and log in.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = {
        // Every test request comes from the same address, so per-address limits would trip.
        // RateLimitTest turns them back on.
        "app.rate-limit.enabled=false",
        // Release missed pickups on every request, so tests don't depend on timing.
        "app.expiry.lazy-min-gap=PT0S"
})
public abstract class ApiTestSupport {

    protected static final String PASSWORD = "correct-horse-battery";

    @Autowired
    protected WebApplicationContext context;

    @Autowired
    protected UserRepository users;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected StoreService stores;

    protected MockMvc mvc;

    @BeforeEach
    void setUpMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /** Creates an approved user straight in the database (owners get a store) and returns a login token. */
    protected String tokenFor(Role role) throws Exception {
        String username = uniqueName();
        User user = new User(username, passwordEncoder.encode(PASSWORD), role);
        user.approve();
        users.save(user);
        if (role == Role.OWNER) {
            stores.createForOwner(user, "Store of " + username);
        }
        return login(username);
    }

    protected String login(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "password": "%s"}
                                """.formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    /** All tests share one database, so each test uses fresh names. */
    protected static String uniqueName() {
        return "user_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
