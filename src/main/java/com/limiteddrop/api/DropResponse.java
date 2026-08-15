package com.limiteddrop.api;

import java.time.Instant;
public record DropResponse(String id, String title, int totalUnits, int availableUnits, Instant opensAt, Integer holdDurationSeconds) { }
