package com.limiteddrop.controller;

import com.limiteddrop.service.HoldCreation;
import com.limiteddrop.service.HoldService;
import com.limiteddrop.domain.HoldState;
import com.limiteddrop.request.CreateHoldRequest;
import com.limiteddrop.response.HoldResponse;
import com.limiteddrop.security.AuthenticatedCustomer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HoldControllerTest {
    @Mock HoldService service;
    private final AuthenticatedCustomer customer = new AuthenticatedCustomer("alice");

    @Test
    void createsNewHoldWithCreatedStatus() {
        HoldResponse response = ControllerTestSupport.response("hold-1", HoldState.ACTIVE);
        when(service.create("drop-1", "alice", new CreateHoldRequest(2), "key"))
                .thenReturn(new HoldCreation(response, false));

        var result = new HoldController(service).create("drop-1", new CreateHoldRequest(2), "key", customer);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isEqualTo(response);
        verify(service).create("drop-1", "alice", new CreateHoldRequest(2), "key");
    }

    @Test
    void replaysHoldWithOkStatus() {
        HoldResponse response = ControllerTestSupport.response("hold-1", HoldState.ACTIVE);
        when(service.create(eq("drop-1"), eq("alice"), eq(new CreateHoldRequest(2)), eq("key")))
                .thenReturn(new HoldCreation(response, true));

        assertThat(new HoldController(service).create("drop-1", new CreateHoldRequest(2), "key", customer).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void delegatesReadConfirmAndCancelUsingCurrentCustomer() {
        HoldResponse active = ControllerTestSupport.response("hold-1", HoldState.ACTIVE);
        when(service.get("hold-1", "alice")).thenReturn(active);
        when(service.confirm("hold-1", "alice", "confirm-key")).thenReturn(active);
        when(service.cancel("hold-1", "alice")).thenReturn(active);
        HoldController controller = new HoldController(service);

        assertThat(controller.get("hold-1", customer)).isEqualTo(active);
        assertThat(controller.confirm("hold-1", "confirm-key", customer)).isEqualTo(active);
        assertThat(controller.cancel("hold-1", customer)).isEqualTo(active);
        verify(service).get("hold-1", "alice");
        verify(service).confirm("hold-1", "alice", "confirm-key");
        verify(service).cancel("hold-1", "alice");
    }
}
