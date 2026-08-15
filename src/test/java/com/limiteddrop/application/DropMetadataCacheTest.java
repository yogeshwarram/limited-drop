package com.limiteddrop.application;

import com.limiteddrop.domain.Drop;
import com.limiteddrop.persistence.DropRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DropMetadataCacheTest {
    @Mock DropRepository repository;

    @Test
    void mapsStaticDropFields() {
        Instant now = Instant.parse("2026-08-15T10:00:00Z");
        when(repository.findById("drop-1")).thenReturn(Optional.of(new Drop("drop-1", "Title", 12, now, 300, now)));

        DropMetadata result = new DropMetadataCache(repository).get("drop-1");

        assertThat(result).isEqualTo(new DropMetadata("drop-1", "Title", 12, now, 300));
    }

    @Test
    void reportsUnknownDrop() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new DropMetadataCache(repository).get("missing"))
                .isInstanceOf(NotFoundException.class);
    }
}
