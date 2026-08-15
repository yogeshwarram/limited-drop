package com.limiteddrop.persistence;

import com.limiteddrop.domain.Drop;
import com.limiteddrop.domain.Hold;
import com.limiteddrop.domain.HoldState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Transactional
class HoldRepositoryQueryTest {
    @Autowired HoldRepository holds;
    @Autowired DropRepository drops;

    @Test
    void findsOnlyExpiredActiveHoldsInExpiryOrderAndPage() {
        Instant now = Instant.parse("2026-08-15T10:00:00Z");
        Drop drop = drops.save(new Drop("drop-1", "Drop", 10, now.minusSeconds(60), null, now));
        Hold later = new Hold("later", drop, "customer", 1, now.minusSeconds(10), "key-later", now);
        Hold confirmed = new Hold("confirmed", drop, "customer", 1, now.minusSeconds(30), "key-confirmed", now);
        confirmed.confirm("confirm", now.minusSeconds(20));
        Hold earlier = new Hold("earlier", drop, "customer", 1, now.minusSeconds(60), "key-earlier", now);
        Hold future = new Hold("future", drop, "customer", 1, now.plusSeconds(60), "key-future", now);
        holds.saveAllAndFlush(List.of(later, confirmed, earlier, future));

        assertThat(holds.findExpiredIds(HoldState.ACTIVE, now, PageRequest.of(0, 1)))
                .containsExactly("earlier");
        assertThat(holds.findExpiredIds(HoldState.ACTIVE, now, PageRequest.of(1, 1)))
                .containsExactly("later");
    }

    @Test
    void findsHoldByCustomerAndIdempotencyKey() {
        Instant now = Instant.parse("2026-08-15T10:00:00Z");
        Drop drop = drops.save(new Drop("drop-1", "Drop", 10, now, null, now));
        holds.saveAndFlush(new Hold("hold-1", drop, "customer", 2, now.plusSeconds(600), "same-key", now));

        assertThat(holds.findByDrop_IdAndCustomerIdAndIdempotencyKey("drop-1", "customer", "same-key"))
                .isPresent().get().extracting(Hold::getId).isEqualTo("hold-1");
        assertThat(holds.findByDrop_IdAndCustomerIdAndIdempotencyKey("drop-1", "other", "same-key"))
                .isEmpty();
    }
}
