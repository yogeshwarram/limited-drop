package com.limiteddrop.api;

import com.limiteddrop.domain.Drop;
import com.limiteddrop.domain.Hold;
import com.limiteddrop.domain.HoldState;

import java.time.Instant;

final class ControllerTestSupport {
    static final Instant NOW = Instant.parse("2026-08-15T10:00:00Z");
    static final Drop DROP = new Drop("drop-1", "Drop", 10, NOW.minusSeconds(1), 300, NOW);

    static HoldResponse response(String id, HoldState state) {
        Hold hold = new Hold(id, DROP, "alice", 2, NOW.plusSeconds(300), "key", NOW);
        if (state == HoldState.CONFIRMED) hold.confirm("confirm", NOW);
        if (state == HoldState.CANCELLED) hold.cancel(NOW);
        if (state == HoldState.EXPIRED) hold.expire();
        return new HoldResponse(hold.getId(), hold.getDrop().getId(), hold.getQuantity(), hold.getState(),
                hold.getExpiresAt(), hold.getCreatedAt(), hold.getConfirmedAt(), hold.getCancelledAt());
    }

    private ControllerTestSupport() { }
}
