package com.limiteddrop.api;

import com.limiteddrop.application.HoldService;
import com.limiteddrop.application.HoldCreation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class HoldController {
    private final HoldService service;
    public HoldController(HoldService service) { this.service = service; }
    @PostMapping("/drops/{dropId}/holds")
    public ResponseEntity<HoldResponse> create(@PathVariable String dropId, @Valid @RequestBody CreateHoldRequest request,
                                                @RequestHeader("Idempotency-Key") String idempotencyKey, @AuthenticationPrincipal Jwt jwt) {
        HoldCreation result = service.create(dropId, jwt.getSubject(), request, idempotencyKey);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(result.hold());
    }
    @GetMapping("/holds/{holdId}")
    public HoldResponse get(@PathVariable String holdId, @AuthenticationPrincipal Jwt jwt) { return service.get(holdId, jwt.getSubject()); }
    @PostMapping("/holds/{holdId}/confirm")
    public HoldResponse confirm(@PathVariable String holdId, @RequestHeader("Idempotency-Key") String idempotencyKey, @AuthenticationPrincipal Jwt jwt) {
        return service.confirm(holdId, jwt.getSubject(), idempotencyKey);
    }
    @DeleteMapping("/holds/{holdId}")
    public HoldResponse cancel(@PathVariable String holdId, @AuthenticationPrincipal Jwt jwt) { return service.cancel(holdId, jwt.getSubject()); }
}
