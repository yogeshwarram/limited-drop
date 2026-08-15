package com.limiteddrop.persistence;

import com.limiteddrop.domain.Drop;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Transactional
class DropRepositoryBehaviorTest {
    @Autowired DropRepository drops;

    @Test
    void openDropReservesAndReturnsExactUnits() {
        Instant now = Instant.now();
        String id = UUID.randomUUID().toString();
        drops.saveAndFlush(new Drop(id, "Drop", 10, now.minusSeconds(1), null, now));

        assertThat(drops.reserveIfAvailable(id, 4)).isEqualTo(1);
        assertThat(drops.findById(id).orElseThrow().getAvailableUnits()).isEqualTo(6);
        assertThat(drops.returnUnits(id, 4)).isEqualTo(1);
        assertThat(drops.findById(id).orElseThrow().getAvailableUnits()).isEqualTo(10);
    }

    @Test
    void futureDropCannotBeReserved() {
        Instant now = Instant.now();
        String id = UUID.randomUUID().toString();
        drops.saveAndFlush(new Drop(id, "Future", 10, now.plusSeconds(3600), null, now));

        assertThat(drops.reserveIfAvailable(id, 1)).isZero();
        assertThat(drops.findById(id).orElseThrow().getAvailableUnits()).isEqualTo(10);
    }

    @Test
    void reservationCannotExceedRemainingCapacityOrUnknownDrop() {
        Instant now = Instant.now();
        String id = UUID.randomUUID().toString();
        drops.saveAndFlush(new Drop(id, "Small", 2, now.minusSeconds(1), null, now));

        assertThat(drops.reserveIfAvailable(id, 3)).isZero();
        assertThat(drops.reserveIfAvailable("missing", 1)).isZero();
        assertThat(drops.findById(id).orElseThrow().getAvailableUnits()).isEqualTo(2);
    }
}
