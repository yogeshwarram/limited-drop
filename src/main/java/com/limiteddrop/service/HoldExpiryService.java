package com.limiteddrop.service;

import com.limiteddrop.domain.Hold;
import com.limiteddrop.domain.HoldState;
import com.limiteddrop.persistence.DropRepository;
import com.limiteddrop.persistence.HoldRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class HoldExpiryService {
    private final HoldRepository holds;
    private final DropRepository drops;
    private final OutboxService outbox;

    public HoldExpiryService(HoldRepository holds, DropRepository drops, OutboxService outbox) {
        this.holds = holds; this.drops = drops; this.outbox = outbox;
    }

    /** Claims once across replicas, then returns inventory once per affected drop. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public int expireNextBatch(Instant now, int batchSize) {
        List<String> ids = holds.claimExpiredIdsForUpdate(HoldState.ACTIVE.name(), now, batchSize);
        if (ids.isEmpty()) return 0;

        Map<String, Integer> releases = new LinkedHashMap<>();
        for (Hold hold : holds.findAllById(ids)) {
            if (hold.getState() != HoldState.ACTIVE || hold.getExpiresAt().isAfter(now)) continue;
            hold.expire();
            releases.merge(hold.getDrop().getId(), hold.getQuantity(), Math::addExact);
            outbox.record("hold.expired", hold, now);
        }
        releases.forEach(drops::returnUnits);
        return releases.values().stream().mapToInt(Integer::intValue).sum();
    }
}
