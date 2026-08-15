package com.limiteddrop.config;

import com.limiteddrop.domain.Drop;
import com.limiteddrop.persistence.DropRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SeedDataConfigurationTest {
    @Test
    void seedsThreeDropsWhenRepositoryIsEmpty() throws Exception {
        Instant now = Instant.parse("2026-08-15T10:00:00Z");
        DropRepository drops = mock(DropRepository.class);
        when(drops.count()).thenReturn(0L);

        new SeedDataConfiguration().seedDrops(drops, Clock.fixed(now, ZoneOffset.UTC))
                .run(new DefaultApplicationArguments());

        verify(drops, times(3)).save(any(Drop.class));
        var captor = org.mockito.ArgumentCaptor.forClass(Drop.class);
        verify(drops, times(3)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Drop::getTitle)
                .containsExactlyInAnyOrder("Friday Night Concert", "Collector Sneaker Release", "Chef's Table - Next Month");
    }

    @Test
    void doesNotReseedExistingRepository() throws Exception {
        DropRepository drops = mock(DropRepository.class);
        when(drops.count()).thenReturn(1L);

        new SeedDataConfiguration().seedDrops(drops, Clock.systemUTC())
                .run(new DefaultApplicationArguments());

        verify(drops, never()).save(any());
    }
}
