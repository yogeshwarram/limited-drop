package com.limiteddrop.response;

import com.limiteddrop.domain.HoldState;
import java.time.Instant;
public record HoldResponse(String id, String dropId, int quantity, HoldState state, Instant expiresAt, Instant createdAt, Instant confirmedAt, Instant cancelledAt) { }
