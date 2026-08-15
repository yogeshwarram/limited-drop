package com.limiteddrop.config;

import com.limiteddrop.domain.Drop;
import com.limiteddrop.persistence.DropRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Configuration
public class SeedDataConfiguration {
    @Bean
    @ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
    ApplicationRunner seedDrops(DropRepository drops, Clock clock) {
        return args -> {
            if (drops.count() != 0) return;
            Instant now = clock.instant();
            drops.save(new Drop(UUID.randomUUID().toString(), "Friday Night Concert", 100, now.minus(Duration.ofHours(1)), 600, now));
            drops.save(new Drop(UUID.randomUUID().toString(), "Collector Sneaker Release", 12, now.minus(Duration.ofMinutes(5)), 300, now));
            drops.save(new Drop(UUID.randomUUID().toString(), "Chef's Table - Next Month", 8, now.plus(Duration.ofDays(2)), 900, now));
        };
    }
}
