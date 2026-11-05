package com.thriftbite.backend.auth;

import com.jayway.jsonpath.JsonPath;
import com.thriftbite.backend.TestcontainersConfiguration;
import com.thriftbite.backend.user.Role;
import com.thriftbite.backend.user.User;
import com.thriftbite.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AuthFlowTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void consumerCanRegisterLogInAndSeeTheirProfile() throws Exception {
        String username = uniqueName();

        register(username, PASSWORD, "CONSUMER")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.role").value("CONSUMER"))
                .andExpect(jsonPath("$.approved").value(true))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        String token = loginToken(username, PASSWORD);

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));
    }

    @Test
    void passwordsAreStoredAsBcryptHashes() throws Exception {
        String username = uniqueName();
        register(username, PASSWORD, "CONSUMER").andExpect(status().isCreated());

        String stored = users.findByUsername(username).orElseThrow().getPasswordHash();
        assertThat(stored).isNotEqualTo(PASSWORD).startsWith("$2");
    }

    @Test
    void usernamesAreCaseInsensitive() throws Exception {
        String username = uniqueName();
        register(username, PASSWORD, "CONSUMER").andExpect(status().isCreated());

        register(username.toUpperCase(), PASSWORD, "CONSUMER").andExpect(status().isConflict());
        login(username.toUpperCase(), PASSWORD).andExpect(status().isOk());
    }

    @Test
    void wrongPasswordAndUnknownUserGetTheSameAnswer() throws Exception {
        String username = uniqueName();
        register(username, PASSWORD, "CONSUMER").andExpect(status().isCreated());

        login(username, "wrong-password-123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));
        login(uniqueName(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));
    }

    @Test
    void ownerCannotLogInUntilAnAdminApproves() throws Exception {
        String owner = uniqueName();
        String ownerId = JsonPath.read(register(owner, PASSWORD, "OWNER")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.approved").value(false))
                .andReturn().getResponse().getContentAsString(), "$.id");

        login(owner, PASSWORD).andExpect(status().isForbidden());

        String adminToken = loginToken(createAdmin(), PASSWORD);
        mvc.perform(post("/api/admin/users/{id}/approve", ownerId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approved").value(true));

        login(owner, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void nobodyCanSignUpAsAdmin() throws Exception {
        register(uniqueName(), PASSWORD, "ADMIN").andExpect(status().isBadRequest());
    }

    @Test
    void nonAdminsAreBlockedFromAdminEndpoints() throws Exception {
        String username = uniqueName();
        register(username, PASSWORD, "CONSUMER").andExpect(status().isCreated());
        String token = loginToken(username, PASSWORD);

        mvc.perform(get("/api/admin/owners/pending").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void protectedEndpointsRequireAValidToken() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidSignupsAreRejected() throws Exception {
        register(uniqueName(), "short", "CONSUMER").andExpect(status().isBadRequest());
        register("no spaces allowed", PASSWORD, "CONSUMER").andExpect(status().isBadRequest());
    }

    // --- helpers ---

    private ResultActions register(String username, String password, String accountType) throws Exception {
        return mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username": "%s", "password": "%s", "accountType": "%s"}
                        """.formatted(username, password, accountType)));
    }

    private ResultActions login(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username": "%s", "password": "%s"}
                        """.formatted(username, password)));
    }

    private String loginToken(String username, String password) throws Exception {
        String body = login(username, password)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private String createAdmin() {
        String username = uniqueName();
        users.save(new User(username, passwordEncoder.encode(PASSWORD), Role.ADMIN));
        return username;
    }

    /** All tests share one database, so every test uses fresh usernames. */
    private static String uniqueName() {
        return "user_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
