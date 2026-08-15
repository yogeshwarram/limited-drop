package com.limiteddrop.controller;

import com.limiteddrop.service.HoldService;
import com.limiteddrop.service.HoldCreation;
import com.limiteddrop.request.CreateHoldRequest;
import com.limiteddrop.response.HoldResponse;
import com.limiteddrop.security.AuthenticatedCustomer;
import com.limiteddrop.security.CurrentCustomer;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class HoldController {
    private final HoldService service;
    public HoldController(HoldService service) { this.service = service; }
    @PostMapping("/drops/{dropId}/holds")
    public ResponseEntity<HoldResponse> create(@PathVariable String dropId, @Valid @RequestBody CreateHoldRequest request,
                                                @RequestHeader("Idempotency-Key") String idempotencyKey, @CurrentCustomer AuthenticatedCustomer customer) {
        HoldCreation result = service.create(dropId, customer.id(), request, idempotencyKey);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(result.hold());
    }
    @GetMapping("/holds/{holdId}")
    public HoldResponse get(@PathVariable String holdId, @CurrentCustomer AuthenticatedCustomer customer) { return service.get(holdId, customer.id()); }
    @PostMapping("/holds/{holdId}/confirm")
    public HoldResponse confirm(@PathVariable String holdId, @RequestHeader("Idempotency-Key") String idempotencyKey, @CurrentCustomer AuthenticatedCustomer customer) {
        return service.confirm(holdId, customer.id(), idempotencyKey);
    }
    @DeleteMapping("/holds/{holdId}")
    public HoldResponse cancel(@PathVariable String holdId, @CurrentCustomer AuthenticatedCustomer customer) { return service.cancel(holdId, customer.id()); }
}
