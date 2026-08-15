package com.limiteddrop.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CreateHoldRequest(@Min(1) @Max(100) int quantity) { }
