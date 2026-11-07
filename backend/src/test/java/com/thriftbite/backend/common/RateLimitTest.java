package com.thriftbite.backend.common;

import com.thriftbite.backend.ApiTestSupport;
import com.thriftbite.backend.common.ratelimit.RateLimitFilter;
import com.thriftbite.backend.user.Role;
import com.thriftbite.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Runs with the limits on (and small), in its own app context. */
@TestPropertySource(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.register-per-window=2",
        "app.rate-limit.login-failures-before-lock=3"
})
class RateLimitTest extends ApiTestSupport {

    @Autowired
    private RateLimitFilter rateLimitFilter;

    /** MockMvc only runs the filters it's given, so add the rate limiter to the usual setup. */
    @BeforeEach
    void addRateLimitFilter() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .addFilters(rateLimitFilter)
                .build();
    }

    @Test
    void oneAddressCanOnlySignUpAFewTimesPerWindow() throws Exception {
        register(uniqueName()).andExpect(status().isCreated());
        register(uniqueName()).andExpect(status().isCreated());

        register(uniqueName())
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.code").value("rate_limited"));
    }

    @Test
    void wrongPasswordsPauseThatUsernameEvenForTheRightPassword() throws Exception {
        String username = uniqueName();
        User user = new User(username, passwordEncoder.encode(PASSWORD), Role.CONSUMER);
        user.approve();
        users.save(user);

        for (int i = 0; i < 3; i++) {
            login(username, "wrong-password-" + i).andExpect(status().isUnauthorized());
        }
        login(username, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("login_locked"));
    }

    @Test
    void usernamesThatDontExistArePausedTheSameWay() throws Exception {
        String nobody = uniqueName();
        for (int i = 0; i < 3; i++) {
            login(nobody, "guess-" + i).andExpect(status().isUnauthorized());
        }
        // same answer as for a real account, so the pause doesn't reveal which usernames exist
        login(nobody, "guess-again").andExpect(jsonPath("$.code").value("login_locked"));
    }

    private ResultActions register(String username) throws Exception {
        return mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username": "%s", "password": "%s", "accountType": "CONSUMER"}
                        """.formatted(username, PASSWORD)));
    }

    private ResultActions login(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username": "%s", "password": "%s"}
                        """.formatted(username, password)));
    }
}
