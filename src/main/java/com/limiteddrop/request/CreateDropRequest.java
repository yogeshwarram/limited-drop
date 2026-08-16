package com.limiteddrop.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public record CreateDropRequest(
        @NotBlank @Size(max = 200) String title,
        @Min(1) int totalUnits,
        @NotNull Instant opensAt,
        @Min(1) Integer holdDurationSeconds) {
    private static final Instant MYSQL_DATETIME_MIN = LocalDateTime.of(1000, 1, 1, 0, 0).toInstant(ZoneOffset.UTC);
    private static final Instant MYSQL_DATETIME_MAX = LocalDateTime.of(9999, 12, 31, 23, 59, 59, 999_999_000).toInstant(ZoneOffset.UTC);

    @AssertTrue(message = "opensAt must be representable by MySQL DATETIME(6)")
    public boolean isOpensAtWithinMysqlDatetimeRange() {
        return opensAt == null || (!opensAt.isBefore(MYSQL_DATETIME_MIN) && !opensAt.isAfter(MYSQL_DATETIME_MAX));
    }
}
