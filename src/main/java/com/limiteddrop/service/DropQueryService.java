package com.limiteddrop.service;

import com.limiteddrop.response.DropResponse;
import com.limiteddrop.domain.Drop;
import com.limiteddrop.persistence.DropRepository;
import com.limiteddrop.exception.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DropQueryService {
    private final DropRepository repository;
    private final DropMetadataCache metadataCache;
    public DropQueryService(DropRepository repository, DropMetadataCache metadataCache) { this.repository = repository; this.metadataCache = metadataCache; }
    public DropResponse get(String id) {
        Drop live = repository.findById(id).orElseThrow(() -> new NotFoundException("Drop not found"));
        DropMetadata metadata = metadataCache.get(id);
        return new DropResponse(metadata.id(), metadata.title(), metadata.totalUnits(), live.getAvailableUnits(), metadata.opensAt(), metadata.holdDurationSeconds());
    }
    public List<DropResponse> list() {
        return repository.findAll().stream().map(d -> new DropResponse(d.getId(), d.getTitle(), d.getTotalUnits(), d.getAvailableUnits(), d.getOpensAt(), d.getHoldDurationSeconds())).toList();
    }
}
