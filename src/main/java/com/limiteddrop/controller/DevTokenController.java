package com.limiteddrop.controller;

import com.limiteddrop.security.DemoTokenService;
import com.limiteddrop.request.DevTokenRequest;
import com.limiteddrop.response.DevTokenResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dev/tokens")
public class DevTokenController {
    private final DemoTokenService tokens;
    public DevTokenController(DemoTokenService tokens) { this.tokens = tokens; }
    @PostMapping
    public ResponseEntity<DevTokenResponse> issue(@Valid @RequestBody DevTokenRequest request) {
        String token = request.scopes() == null || request.scopes().isEmpty() ? tokens.issue(request.customerId()) : tokens.issue(request.customerId(), request.scopes());
        return ResponseEntity.ok(new DevTokenResponse(token, "Bearer", 3600));
    }
}
