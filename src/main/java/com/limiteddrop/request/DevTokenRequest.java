package com.limiteddrop.request;

import jakarta.validation.constraints.NotBlank;
public record DevTokenRequest(@NotBlank String customerId) { }
