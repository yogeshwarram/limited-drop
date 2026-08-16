package com.limiteddrop.service;

import com.limiteddrop.config.ReservationProperties;
import com.limiteddrop.exception.ReservationBusyException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DropAdmissionControllerTest {
    @Test
    void rejectsAHotDropWhenAllPermitsAreInUse() {
        ReservationProperties properties = new ReservationProperties();
        properties.getReservations().setAdmissionPermits(1);
        properties.getReservations().setAdmissionTimeout(java.time.Duration.ZERO);
        DropAdmissionController controller = new DropAdmissionController(properties);

        try (DropAdmissionController.Permit ignored = controller.acquire("drop-1")) {
            assertThatThrownBy(() -> controller.acquire("drop-1"))
                    .isInstanceOf(ReservationBusyException.class);
        }
    }
}
