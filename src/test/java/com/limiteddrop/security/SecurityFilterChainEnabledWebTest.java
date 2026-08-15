package com.limiteddrop.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import com.limiteddrop.security.DemoTokenService;
import com.limiteddrop.application.DropQueryService;
import com.limiteddrop.application.HoldService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@Import(SecurityConfiguration.class)
@TestPropertySource(properties = {
        "app.security.dev-token-endpoint-enabled=true",
        "app.security.hmac-secret=test-secret-with-at-least-thirty-two-bytes"
})
class SecurityFilterChainEnabledWebTest {
    @Autowired MockMvc mvc;
    @MockBean JwtDecoder jwtDecoder;
    @MockBean Clock clock;
    @MockBean DemoTokenService tokens;
    @MockBean DropQueryService drops;
    @MockBean HoldService holds;

    @Test
    void allowsDevelopmentTokenEndpointWhenEnabled() throws Exception {
        mvc.perform(post("/api/v1/dev/tokens")).andExpect(status().isBadRequest());
    }
}
