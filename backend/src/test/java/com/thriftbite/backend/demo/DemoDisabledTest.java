package com.thriftbite.backend.demo;

import com.thriftbite.backend.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Demo mode is off unless asked for: no one-tap logins on a real deployment. */
class DemoDisabledTest extends ApiTestSupport {

    @Test
    void demoEndpointsDontExistByDefault() throws Exception {
        mvc.perform(get("/api/demo")).andExpect(status().isNotFound());
        mvc.perform(post("/api/demo/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"as\": \"ADMIN\"}"))
                .andExpect(status().isNotFound());
    }
}
