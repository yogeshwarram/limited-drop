package com.limiteddrop.api;

import jakarta.validation.constraints.NotBlank;
public record DevTokenRequest(@NotBlank String customerId) { }
