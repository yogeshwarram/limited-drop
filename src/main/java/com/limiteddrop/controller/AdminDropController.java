package com.limiteddrop.controller;

import com.limiteddrop.request.BulkCapacityAdjustmentRequest;
import com.limiteddrop.request.BulkCreateDropsRequest;
import com.limiteddrop.request.CapacityAdjustmentRequest;
import com.limiteddrop.request.CreateDropRequest;
import com.limiteddrop.response.AdminAuditResponse;
import com.limiteddrop.response.AdminBulkCapacityResponse;
import com.limiteddrop.response.AdminBulkDropsResponse;
import com.limiteddrop.response.DropResponse;
import com.limiteddrop.security.AuthenticatedCustomer;
import com.limiteddrop.security.CurrentCustomer;
import com.limiteddrop.service.AdminDropService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/drops")
public class AdminDropController {
    private final AdminDropService service;
    public AdminDropController(AdminDropService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<DropResponse> create(@CurrentCustomer AuthenticatedCustomer admin, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody CreateDropRequest request) {
        var result = service.create(admin.id(), key, request);
        return ResponseEntity.status(result.status()).body(result.body());
    }

    @PostMapping("/bulk")
    public ResponseEntity<AdminBulkDropsResponse> createBulk(@CurrentCustomer AuthenticatedCustomer admin, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody BulkCreateDropsRequest request) {
        var result = service.createBulk(admin.id(), key, request);
        return ResponseEntity.status(result.status()).body(result.body());
    }

    @PostMapping("/{dropId}/capacity-adjustments")
    public ResponseEntity<DropResponse> addCapacity(@CurrentCustomer AuthenticatedCustomer admin, @PathVariable String dropId, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody CapacityAdjustmentRequest request) {
        var result = service.addCapacity(admin.id(), key, dropId, request);
        return ResponseEntity.status(result.status()).body(result.body());
    }

    @PostMapping("/capacity-adjustments/bulk")
    public ResponseEntity<AdminBulkCapacityResponse> addCapacityBulk(@CurrentCustomer AuthenticatedCustomer admin, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody BulkCapacityAdjustmentRequest request) {
        var result = service.addCapacityBulk(admin.id(), key, request);
        return ResponseEntity.status(result.status()).body(result.body());
    }

    @GetMapping("/{dropId}/audit")
    public Page<AdminAuditResponse> audit(@PathVariable String dropId, @PageableDefault(size = 20) Pageable pageable) {
        return service.audit(dropId, pageable);
    }
}
