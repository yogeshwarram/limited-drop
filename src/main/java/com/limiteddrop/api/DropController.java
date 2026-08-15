package com.limiteddrop.api;

import com.limiteddrop.application.DropQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/v1/drops")
public class DropController {
    private final DropQueryService service;
    public DropController(DropQueryService service) { this.service = service; }
    @GetMapping public List<DropResponse> list() { return service.list(); }
    @GetMapping("/{dropId}") public DropResponse get(@PathVariable String dropId) { return service.get(dropId); }
}
