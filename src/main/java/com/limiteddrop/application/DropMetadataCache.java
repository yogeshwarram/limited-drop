package com.limiteddrop.application;

import com.limiteddrop.domain.Drop;
import com.limiteddrop.persistence.DropRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class DropMetadataCache {
    private final DropRepository dropRepository;
    public DropMetadataCache(DropRepository dropRepository) { this.dropRepository = dropRepository; }
    @Cacheable(cacheNames = "drop-metadata", key = "#dropId")
    public DropMetadata get(String dropId) {
        Drop drop = dropRepository.findById(dropId).orElseThrow(() -> new NotFoundException("Drop not found"));
        return new DropMetadata(drop.getId(), drop.getTitle(), drop.getTotalUnits(), drop.getOpensAt(), drop.getHoldDurationSeconds());
    }
}
