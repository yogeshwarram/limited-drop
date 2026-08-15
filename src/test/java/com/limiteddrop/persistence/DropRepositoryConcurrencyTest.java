package com.limiteddrop.persistence;

import com.limiteddrop.domain.Drop;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class DropRepositoryConcurrencyTest {
    @Autowired DropRepository drops;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void conditionalUpdateNeverReservesMoreThanAvailableUnderContention() throws Exception {
        String id = UUID.randomUUID().toString();
        drops.saveAndFlush(new Drop(id, "Contention test", 5, Instant.now().minusSeconds(60), null, Instant.now()));
        ExecutorService pool = Executors.newFixedThreadPool(20);
        try {
            List<Callable<Integer>> attempts = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                attempts.add(() -> new TransactionTemplate(transactionManager).execute(status -> drops.reserveIfAvailable(id, 1)));
            }
            int successes = pool.invokeAll(attempts).stream().mapToInt(future -> {
                try { return future.get(); } catch (Exception exception) { throw new AssertionError(exception); }
            }).sum();
            assertThat(successes).isEqualTo(5);
            assertThat(drops.findById(id).orElseThrow().getAvailableUnits()).isZero();
        } finally {
            pool.shutdownNow();
        }
    }
}
