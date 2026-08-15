package com.limiteddrop.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateDropRequest(
        @NotBlank @Size(max = 200) String title,
        @Min(1) int totalUnits,
        @NotNull Instant opensAt,
        @Min(1) Integer holdDurationSeconds) { }
