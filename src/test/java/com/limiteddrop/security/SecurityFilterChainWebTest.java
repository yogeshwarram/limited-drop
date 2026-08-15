package com.limiteddrop.security;

import com.limiteddrop.api.DropController;
import com.limiteddrop.application.DropQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@WebMvcTest(DropController.class)
@Import(SecurityConfiguration.class)
@TestPropertySource(properties = {
        "app.security.dev-token-endpoint-enabled=false",
        "app.security.hmac-secret=test-secret-with-at-least-thirty-two-bytes"
})
class SecurityFilterChainWebTest {
    @Autowired MockMvc mvc;
    @MockBean DropQueryService drops;
    @MockBean JwtDecoder jwtDecoder;
    @MockBean Clock clock;

    @Test
    void requiresAuthenticationForApplicationEndpoints() throws Exception {
        mvc.perform(get("/api/v1/drops")).andExpect(status().isUnauthorized());
    }

    @Test
    void deniesDisabledDevelopmentTokenEndpoint() throws Exception {
        mvc.perform(postTokenEndpoint().with(jwt())).andExpect(status().isForbidden());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postTokenEndpoint() {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/dev/tokens");
    }
}
