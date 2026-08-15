package com.limiteddrop.response;

import java.time.Instant;

public record AdminAuditResponse(String id, String dropId, String actor, String operation, String reason,
                                 Integer beforeTotalUnits, Integer beforeAvailableUnits,
                                 Integer afterTotalUnits, Integer afterAvailableUnits, Instant occurredAt) { }
