package com.openfloat.mpesa.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "openfloat.rate-limit.requests-per-minute=5",
        "openfloat.rate-limit.window-seconds=60"
}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RateLimitIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testRateLimiterFiresAfterLimitExceeded() throws Exception {
        // Make 5 requests - all should be accepted and return HTTP 200
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/v1/transactions")
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                            .header("X-Forwarded-For", "192.168.1.100"))
                    .andExpect(status().isOk());
        }

        // The 6th request from the same IP should be blocked with 429 Too Many Requests
        mockMvc.perform(get("/api/v1/transactions")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .header("X-Forwarded-For", "192.168.1.100"))
                .andExpect(status().isTooManyRequests());

        // A request from a different IP should be accepted and return HTTP 200
        mockMvc.perform(get("/api/v1/transactions")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .header("X-Forwarded-For", "192.168.1.101"))
                .andExpect(status().isOk());
    }
}
