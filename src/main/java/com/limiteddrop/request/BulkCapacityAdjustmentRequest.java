package com.limiteddrop.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BulkCapacityAdjustmentRequest(@NotEmpty @Size(max = 100) List<@Valid CapacityAdjustmentItem> adjustments) {
    public record CapacityAdjustmentItem(@NotBlank String dropId, @Min(1) int quantity, @NotBlank @Size(max = 500) String reason) { }
}
