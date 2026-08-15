package com.limiteddrop.security;

import com.limiteddrop.controller.AdminDropController;
import com.limiteddrop.request.CreateDropRequest;
import com.limiteddrop.response.DropResponse;
import com.limiteddrop.service.AdminDropService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminDropController.class)
@Import(SecurityConfiguration.class)
@TestPropertySource(properties = "app.security.hmac-secret=test-secret-with-at-least-thirty-two-bytes")
class AdminDropSecurityWebTest {
    @Autowired MockMvc mvc;
    @MockBean AdminDropService service;
    @MockBean JwtDecoder jwtDecoder;
    @MockBean Clock clock;

    @Test
    void allowsAdminScope() throws Exception {
        when(service.create(any(), any(), any(CreateDropRequest.class)))
                .thenReturn(new AdminDropService.AdminCommandResult<>(new DropResponse("drop-1", "Drop", 2, 2, Instant.now(), null), 201, false));
        mvc.perform(post("/api/v1/admin/drops")
                        .with(jwt().jwt(jwt -> jwt.subject("admin").claim("scope", "drops:manage")))
                        .header("Idempotency-Key", "key")
                        .contentType("application/json")
                        .content("{\"title\":\"Drop\",\"totalUnits\":2,\"opensAt\":\"2026-08-15T10:00:00Z\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void deniesAuthenticatedCustomerWithoutAdminScope() throws Exception {
        mvc.perform(post("/api/v1/admin/drops")
                        .with(jwt().jwt(jwt -> jwt.subject("customer")))
                        .header("Idempotency-Key", "key")
                        .contentType("application/json")
                        .content("{\"title\":\"Drop\",\"totalUnits\":2,\"opensAt\":\"2026-08-15T10:00:00Z\"}"))
                .andExpect(status().isForbidden());
    }
}
