package com.limiteddrop.service;

import com.limiteddrop.response.DropResponse;
import com.limiteddrop.domain.Drop;
import com.limiteddrop.persistence.DropRepository;
import com.limiteddrop.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DropQueryServiceTest {
    private static final Instant OPEN = Instant.parse("2026-08-15T10:00:00Z");
    @Mock DropRepository repository;
    @Mock DropMetadataCache metadataCache;

    @Test
    void getCombinesCachedMetadataWithLiveInventory() {
        Drop live = new Drop("drop-1", "Current title", 10, OPEN, 120, OPEN);
        when(repository.findById("drop-1")).thenReturn(Optional.of(live));
        when(metadataCache.get("drop-1")).thenReturn(new DropMetadata("drop-1", "Original title", 10, OPEN, 120));

        DropResponse result = new DropQueryService(repository, metadataCache).get("drop-1");

        assertThat(result.title()).isEqualTo("Original title");
        assertThat(result.totalUnits()).isEqualTo(10);
        assertThat(result.availableUnits()).isEqualTo(10);
        verify(metadataCache).get("drop-1");
    }

    @Test
    void getFailsBeforeReadingMetadataForUnknownDrop() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new DropQueryService(repository, metadataCache).get("missing"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Drop not found");
        verifyNoInteractions(metadataCache);
    }

    @Test
    void listUsesLiveRowsAndDoesNotReadTheMetadataCache() {
        Drop first = new Drop("first", "First", 4, OPEN, null, OPEN);
        Drop second = new Drop("second", "Second", 8, OPEN.plusSeconds(30), 300, OPEN);
        when(repository.findAll()).thenReturn(List.of(first, second));

        List<DropResponse> result = new DropQueryService(repository, metadataCache).list();

        assertThat(result).extracting(DropResponse::id).containsExactly("first", "second");
        assertThat(result).extracting(DropResponse::availableUnits).containsExactly(4, 8);
        assertThat(result.get(0).holdDurationSeconds()).isNull();
        verifyNoInteractions(metadataCache);
    }

    @Test
    void getReturnsLiveHoldingsAfterMetadataCacheLookup() {
        Drop live = new Drop("drop-1", "Drop", 10, OPEN, null, OPEN);
        when(repository.findById("drop-1")).thenReturn(Optional.of(live));
        when(metadataCache.get("drop-1")).thenReturn(new DropMetadata("drop-1", "Drop", 10, OPEN, null));

        assertThat(new DropQueryService(repository, metadataCache).get("drop-1").availableUnits()).isEqualTo(10);
    }
}
