package com.limiteddrop.api;

import com.limiteddrop.security.DemoTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DevTokenControllerTest {
    @Mock DemoTokenService tokens;

    @Test
    void issuesBearerResponseForCustomer() {
        when(tokens.issue("alice")).thenReturn("jwt");

        var result = new DevTokenController(tokens).issue(new DevTokenRequest("alice"));

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isEqualTo(new DevTokenResponse("jwt", "Bearer", 3600));
        verify(tokens).issue("alice");
    }
}
