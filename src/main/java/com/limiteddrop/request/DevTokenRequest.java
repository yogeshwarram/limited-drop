package com.limiteddrop.request;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record DevTokenRequest(@NotBlank String customerId, List<String> scopes) {
    public DevTokenRequest(String customerId) { this(customerId, List.of()); }
}
